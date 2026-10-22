import { createHmac, randomUUID, timingSafeEqual } from "node:crypto";
import { createClient } from "@supabase/supabase-js";
import { DomainError } from "./commerce";
import { demoMode } from "./store";

export type User = { id: string; email: string };
export function appUrl(): string {
  const url = new URL(process.env.APP_URL ?? "http://127.0.0.1:3000");
  if (
    url.protocol !== "https:" &&
    !["127.0.0.1", "localhost"].includes(url.hostname)
  )
    throw new Error("APP_URL must use HTTPS");
  return url.origin;
}
export function assertLocalDemo(request: Request) {
  if (
    !demoMode() ||
    !["localhost", "127.0.0.1"].includes(new URL(request.url).hostname) ||
    !["localhost", "127.0.0.1"].includes(new URL(appUrl()).hostname)
  )
    throw new DomainError("Local demo is disabled", 403);
}
export function checkOrigin(request: Request) {
  if (request.headers.get("origin") !== appUrl())
    throw new DomainError("Invalid request origin", 403);
}
const hmac = (value: string): string => {
  const secret = process.env.VOXA_SESSION_SECRET;
  if (!secret || secret.length < 32)
    throw new Error("Session secret is not configured");
  return createHmac("sha256", secret).update(value).digest("base64url");
};
export function demoCookie(): string {
  const value = Buffer.from(
    JSON.stringify({ id: randomUUID(), exp: Date.now() + 24 * 3600_000 }),
  ).toString("base64url");
  return `voxa_demo=${value}.${hmac(value)}; HttpOnly; SameSite=Strict; Path=/; Max-Age=86400`;
}
export async function authenticate(request: Request): Promise<User> {
  if (demoMode()) {
    assertLocalDemo(request);
    const value = request.headers
      .get("cookie")
      ?.split(";")
      .map((s) => s.trim())
      .find((s) => s.startsWith("voxa_demo="))
      ?.slice(10);
    if (!value) throw new DomainError("Sign in to continue", 401);
    const [body, signature] = value.split(".");
    const expected = Buffer.from(hmac(body));
    const received = Buffer.from(signature ?? "");
    if (
      expected.length !== received.length ||
      !timingSafeEqual(expected, received)
    )
      throw new DomainError("Invalid session", 401);
    const session = JSON.parse(Buffer.from(body, "base64url").toString());
    if (session.exp < Date.now()) throw new DomainError("Session expired", 401);
    return { id: session.id, email: "demo@voxa.local" };
  }
  const token = request.headers.get("authorization")?.replace(/^Bearer /, "");
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!token) throw new DomainError("Sign in to continue", 401);
  if (!url || !key)
    throw new DomainError("Authentication is not configured", 503);
  const client = createClient(url, key, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data, error } = await client.auth.getUser(token);
  if (error || !data.user?.email)
    throw new DomainError("Session expired. Sign in again.", 401);
  return { id: data.user.id, email: data.user.email };
}
export async function bodyText(
  request: Request,
  limit = 65536,
): Promise<string> {
  const reader = request.body?.getReader();
  if (!reader) return "";
  const chunks: Uint8Array[] = [];
  let size = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.length;
    if (size > limit) {
      await reader.cancel();
      throw new DomainError("Request is too large", 413);
    }
    chunks.push(value);
  }
  return Buffer.concat(chunks).toString("utf8");
}
export async function jsonBody(request: Request): Promise<unknown> {
  try {
    return JSON.parse(await bodyText(request));
  } catch (error) {
    if (error instanceof DomainError) throw error;
    throw new DomainError("Invalid JSON");
  }
}
const buckets = new Map<string, { count: number; reset: number }>();
export function rateLimit(key: string, max = 30) {
  const now = Date.now();
  if (buckets.size > 10000)
    for (const [id, value] of buckets)
      if (value.reset < now) buckets.delete(id);
  const bucket = buckets.get(key);
  if (!bucket || bucket.reset < now) {
    buckets.set(key, { count: 1, reset: now + 60_000 });
    return;
  }
  if (++bucket.count > max)
    throw new DomainError("Too many requests. Please try again shortly.", 429);
}
export function response(value: unknown, status = 200): Response {
  return Response.json(value, {
    status,
    headers: { "Cache-Control": "no-store", "Referrer-Policy": "no-referrer" },
  });
}
