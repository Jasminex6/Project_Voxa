import { createHmac, randomInt, randomUUID, timingSafeEqual } from "node:crypto";
import { z } from "zod";
import { newLicenseKey, signLicense } from "./license";
import { DomainError } from "./commerce";

// Hosted demonstrations have a separate signer, trusted by demo APKs only.
// The 100 reusable demonstration codes are intentionally shareable across test phones.
// Each redemption still returns a signed license bound to the requesting installation.
export const publicDemoCodePattern = /^VOXA-[FC]-\d{3}-[A-F0-9]{8}$/i;
export const publicDemoSchema = z.object({
  tier: z.enum(["FAMILY_PRO", "CLINICAL_SLP"]),
}).strict();

export function publicDemoEnabled(): boolean {
  return process.env.VOXA_PUBLIC_DEMO === "true" &&
    !!process.env.VOXA_PUBLIC_DEMO_KEY_ID && !!process.env.VOXA_PUBLIC_DEMO_PRIVATE_KEY;
}

function requireDemo() {
  if (!publicDemoEnabled()) throw new DomainError("Plan demonstration is not enabled", 503);
}
function demoCode(tier: "FAMILY_PRO" | "CLINICAL_SLP", index: number) {
  const label = `${tier === "FAMILY_PRO" ? "F" : "C"}-${String(index).padStart(3, "0")}`;
  const checksum = createHmac("sha256", process.env.VOXA_PUBLIC_DEMO_PRIVATE_KEY!.replace(/\\n/g, "\n"))
    .update(`voxa-demo-pool-v1:${label}`).digest("hex").slice(0, 8).toUpperCase();
  return `VOXA-${label}-${checksum}`;
}
export function publicDemoCodes() {
  requireDemo();
  return (["FAMILY_PRO", "CLINICAL_SLP"] as const).flatMap(tier =>
    Array.from({ length: 50 }, (_, i) => ({ tier, code: demoCode(tier, i + 1) })));
}
export function issuePublicDemo(input: unknown) {
  requireDemo();
  const { tier } = publicDemoSchema.parse(input);
  const code = demoCode(tier, randomInt(1, 51));
  return { code, deepLink: `voxa://activate?code=${code}`, tier };
}
export function redeemPublicDemo(codeInput: string, deviceIdInput: string, now = Date.now()) {
  requireDemo();
  const deviceId = z.string().uuid().parse(deviceIdInput);
  const code = codeInput.trim().toUpperCase();
  if (!publicDemoCodePattern.test(code)) throw new DomainError("Invalid demo code");
  const [, label, number] = code.split("-");
  const index = Number(number);
  if (index < 1 || index > 50) throw new DomainError("Invalid demo code");
  const tier = label === "F" ? "FAMILY_PRO" : "CLINICAL_SLP";
  const expected = demoCode(tier, index);
  if (!timingSafeEqual(Buffer.from(code), Buffer.from(expected))) throw new DomainError("Invalid demo code");
  const expiresAt = now + 7 * 86400_000;
  const token = signLicense({
    userId: randomUUID(), licenseKey: newLicenseKey(), tier, deviceId,
    devices: 1, expiresAt,
  }, now, {
    kid: process.env.VOXA_PUBLIC_DEMO_KEY_ID!,
    pem: process.env.VOXA_PUBLIC_DEMO_PRIVATE_KEY!,
  });
  return { token, tier, expiresAt, devices: 1 };
}
