import { randomUUID } from "node:crypto";
import { z } from "zod";
import { newLicenseKey, signLicense } from "./license";
import { DomainError } from "./commerce";

// Hosted demonstrations have a separate signer, trusted by demo APKs only.
// No payment, account, child information, database, or shared bearer code is involved.
export const publicDemoSchema = z.object({
  tier: z.enum(["FAMILY_PRO", "CLINICAL_SLP"]),
  deviceId: z.string().uuid(),
}).strict();

export function publicDemoEnabled(): boolean {
  return process.env.VOXA_PUBLIC_DEMO === "true" &&
    !!process.env.VOXA_PUBLIC_DEMO_KEY_ID && !!process.env.VOXA_PUBLIC_DEMO_PRIVATE_KEY;
}

export function issuePublicDemo(input: unknown, now = Date.now()) {
  if (!publicDemoEnabled()) throw new DomainError("Plan demonstration is not enabled", 503);
  const { tier, deviceId } = publicDemoSchema.parse(input);
  const expiresAt = now + 7 * 86400_000;
  const token = signLicense({
    userId: randomUUID(), licenseKey: newLicenseKey(), tier, deviceId,
    devices: 1, expiresAt,
  }, now, {
    kid: process.env.VOXA_PUBLIC_DEMO_KEY_ID!,
    pem: process.env.VOXA_PUBLIC_DEMO_PRIVATE_KEY!,
  });
  return { token, deepLink: `voxa://activate?token=${token}`, tier, expiresAt, devices: 1 };
}
