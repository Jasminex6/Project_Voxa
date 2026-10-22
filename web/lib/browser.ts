"use client";
import { createClient, type SupabaseClient } from "@supabase/supabase-js";
let client: SupabaseClient | null = null;
export function authClient(): SupabaseClient | null {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL,
    key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!url || !key) return null;
  client ??= createClient(url, key);
  return client;
}
export async function api<T>(path: string, body?: unknown): Promise<T> {
  const session = await authClient()?.auth.getSession();
  const token = session?.data.session?.access_token;
  const result = await fetch(`/api/${path}`, {
    method: body === undefined ? "GET" : "POST",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const data = await result.json().catch(() => ({}));
  if (!result.ok) throw new Error(data.error ?? "Please try again.");
  return data;
}
