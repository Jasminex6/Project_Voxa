import {
  createPrivateKey,
  createPublicKey,
  randomBytes,
  randomUUID,
  sign,
  verify,
} from "node:crypto";
import { z } from "zod";
import { tiers, type Tier } from "./catalog";

export const claimsSchema = z
  .object({
    iss: z.literal("voxa-license"),
    aud: z.literal("voxa-android"),
    sub: z.string().uuid(),
    jti: z.string().uuid(),
    key: z.string().regex(/^VOXA-[A-F0-9]{4}(-[A-F0-9]{4}){3}$/),
    tier: z.enum(tiers),
    device_id: z.string().uuid(),
    devices_allowed: z.number().int().positive().max(100),
    iat: z.number().int().nonnegative(),
    exp: z.number().int().positive(),
    grace_until: z.number().int().positive(),
  })
  .strict();
export type LicenseClaims = z.infer<typeof claimsSchema>;
export function newLicenseKey(): string {
  return (
    "VOXA-" +
    randomBytes(8).toString("hex").toUpperCase().match(/.{4}/g)!.join("-")
  );
}
export function signLicense(
  input: {
    userId: string;
    licenseKey: string;
    tier: Tier;
    deviceId: string;
    devices: number;
    expiresAt: number;
  },
  now = Date.now(),
): string {
  const kid = process.env.VOXA_LICENSE_KEY_ID;
  const pem = process.env.VOXA_LICENSE_PRIVATE_KEY?.replace(/\\n/g, "\n");
  if (!kid || !pem) throw new Error("License signing is not configured");
  const claims = claimsSchema.parse({
    iss: "voxa-license",
    aud: "voxa-android",
    sub: input.userId,
    jti: randomUUID(),
    key: input.licenseKey,
    tier: input.tier,
    device_id: input.deviceId,
    devices_allowed: input.devices,
    iat: Math.floor(now / 1000),
    exp: Math.floor(input.expiresAt / 1000),
    grace_until: Math.floor(input.expiresAt / 1000) + 7 * 86400,
  });
  if (claims.exp <= claims.iat)
    throw new Error("Cannot issue an expired license");
  const key = createPrivateKey(pem);
  if (key.asymmetricKeyType !== "ed25519")
    throw new Error("An Ed25519 signing key is required");
  const body = [{ alg: "EdDSA", typ: "JWT", kid }, claims]
    .map((value) => Buffer.from(JSON.stringify(value)).toString("base64url"))
    .join(".");
  return `${body}.${sign(null, Buffer.from(body), key).toString("base64url")}`;
}
export function verifyLicense(
  token: string,
  keys: Record<string, string>,
  deviceId: string,
  now = Date.now(),
): LicenseClaims {
  if (token.length > 8192) throw new Error("Invalid token");
  const parts = token.split(".");
  if (parts.length !== 3 || parts.some((p) => !/^[A-Za-z0-9_-]+$/.test(p)))
    throw new Error("Invalid token");
  const header = JSON.parse(Buffer.from(parts[0], "base64url").toString());
  if (
    header.alg !== "EdDSA" ||
    header.typ !== "JWT" ||
    !Object.hasOwn(keys, header.kid)
  )
    throw new Error("Untrusted key");
  const signature = Buffer.from(parts[2], "base64url");
  if (
    signature.length !== 64 ||
    !verify(
      null,
      Buffer.from(parts.slice(0, 2).join(".")),
      createPublicKey(keys[header.kid]),
      signature,
    )
  )
    throw new Error("Invalid signature");
  const claims = claimsSchema.parse(
    JSON.parse(Buffer.from(parts[1], "base64url").toString()),
  );
  const seconds = Math.floor(now / 1000);
  if (
    claims.device_id !== deviceId ||
    claims.iat > seconds + 300 ||
    claims.exp <= claims.iat ||
    claims.grace_until !== claims.exp + 7 * 86400 ||
    seconds >= claims.grace_until
  )
    throw new Error("Expired or mismatched license");
  return claims;
}
