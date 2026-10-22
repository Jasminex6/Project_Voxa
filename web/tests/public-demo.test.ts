import test from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, randomUUID } from "node:crypto";
import { issuePublicDemo } from "../lib/public-demo";
import { verifyLicense } from "../lib/license";

const pair = generateKeyPairSync("ed25519");
const keys = { "hosted-demo-test": pair.publicKey.export({ type: "spki", format: "pem" }).toString() };
process.env.VOXA_PUBLIC_DEMO = "true";
process.env.VOXA_PUBLIC_DEMO_KEY_ID = "hosted-demo-test";
process.env.VOXA_PUBLIC_DEMO_PRIVATE_KEY = pair.privateKey.export({ type: "pkcs8", format: "pem" }).toString();

test("hosted demonstration signs the chosen tier for exactly one installation", () => {
  const now = Date.now(), deviceId = randomUUID();
  for (const tier of ["FAMILY_PRO", "CLINICAL_SLP"]) {
    const demo = issuePublicDemo({ tier, deviceId }, now);
    const claims = verifyLicense(demo.token, keys, deviceId, now);
    assert.equal(claims.tier, tier);
    assert.equal(claims.devices_allowed, 1);
    assert.equal(demo.expiresAt, now + 7 * 86400_000);
    assert.equal(claims.exp, Math.floor(demo.expiresAt / 1000));
    assert.equal(new URL(demo.deepLink).searchParams.get("token"), demo.token);
    assert.throws(() => verifyLicense(demo.token, keys, randomUUID(), now));
    assert.throws(() => verifyLicense(demo.token, {}, deviceId, now));
    const parts = demo.token.split(".");
    const payload = JSON.parse(Buffer.from(parts[1], "base64url").toString());
    payload.tier = tier === "FAMILY_PRO" ? "CLINICAL_SLP" : "FAMILY_PRO";
    parts[1] = Buffer.from(JSON.stringify(payload)).toString("base64url");
    assert.throws(() => verifyLicense(parts.join("."), keys, deviceId, now));
  }
});
test("demo issuance rejects child fields, invalid IDs, arbitrary tiers and client expiry", () => {
  const good = { tier: "FAMILY_PRO", deviceId: randomUUID() };
  for (const input of [{ ...good, childName: "not allowed" }, { ...good, deviceId: "invalid" },
    { ...good, tier: "ADMIN" }, { ...good, expiresAt: Date.now() + 9999999999 }]) {
    assert.throws(() => issuePublicDemo(input));
  }
});
test("explicit enablement is required for public demo codes", () => {
  const prior = process.env.VOXA_PUBLIC_DEMO;
  process.env.VOXA_PUBLIC_DEMO = "false";
  try { assert.throws(() => issuePublicDemo({ tier: "FAMILY_PRO", deviceId: randomUUID() }), /not enabled/); }
  finally { process.env.VOXA_PUBLIC_DEMO = prior; }
});
