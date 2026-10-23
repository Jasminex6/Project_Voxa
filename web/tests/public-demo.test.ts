import test from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, randomUUID } from "node:crypto";
import { issuePublicDemo, publicDemoCodes, redeemPublicDemo } from "../lib/public-demo";
import { verifyLicense } from "../lib/license";
import { activationDeepLink } from "../lib/activation-link";
const pair = generateKeyPairSync("ed25519");
const keys = { "hosted-demo-test": pair.publicKey.export({ type: "spki", format: "pem" }).toString() };
process.env.VOXA_PUBLIC_DEMO = "true";
process.env.VOXA_PUBLIC_DEMO_KEY_ID = "hosted-demo-test";
process.env.VOXA_PUBLIC_DEMO_PRIVATE_KEY = pair.privateKey.export({ type: "pkcs8", format: "pem" }).toString();
test("100 stable reusable codes select the correct plan on different installations", () => {
  const pool = publicDemoCodes();
  assert.equal(pool.length, 100);
  assert.equal(new Set(pool.map(row => row.code)).size, 100);
  assert.equal(pool.filter(row => row.tier === "FAMILY_PRO").length, 50);
  assert.deepEqual(publicDemoCodes(), pool);
  for (const { code, tier } of pool) {
    const deviceId = randomUUID(), now = Date.now();
    const demo = redeemPublicDemo(code, deviceId, now);
    const claims = verifyLicense(demo.token, keys, deviceId, now);
    assert.equal(claims.tier, tier);
    assert.equal(demo.expiresAt, now + 7 * 86400_000);
    assert.throws(() => verifyLicense(demo.token, keys, randomUUID(), now));
    const second = randomUUID();
    assert.equal(verifyLicense(redeemPublicDemo(code, second, now).token, keys, second, now).tier, tier);
  }
});
test("generated short codes need no device ID and forged plan codes cannot redeem", () => {
  for (const tier of ["FAMILY_PRO", "CLINICAL_SLP"]) {
    const { code, deepLink } = issuePublicDemo({ tier });
    assert.equal(code.length, 19);
    assert.equal(new URL(deepLink).searchParams.get("code"), code);
    const changed = code.replace(tier === "FAMILY_PRO" ? "-F-" : "-C-", tier === "FAMILY_PRO" ? "-C-" : "-F-");
    assert.throws(() => redeemPublicDemo(changed, randomUUID()));
  }
  for (const input of [{ tier: "FAMILY_PRO", childName: "not allowed" }, { tier: "ADMIN" }, { tier: "FAMILY_PRO", expiresAt: 9999999999999 }]) assert.throws(() => issuePublicDemo(input));
  assert.throws(() => redeemPublicDemo(publicDemoCodes()[0].code, "invalid"));
});
test("QR landing page forwards short codes and old paired tokens and rejects ambiguous links", () => {
  const code = publicDemoCodes()[0].code;
  assert.equal(activationDeepLink({ code }), `voxa://activate?code=${code}`);
  assert.equal(activationDeepLink({ token: "abc.def.ghi" }), "voxa://activate?token=abc.def.ghi");
  assert.equal(activationDeepLink({ code, token: "abc.def.ghi" }), null);
  assert.equal(activationDeepLink({ code: [code, code] }), null);
  assert.equal(activationDeepLink({ code: "javascript:alert(1)" }), null);
});
test("public code generation and redemption require explicit demo enablement", () => {
  process.env.VOXA_PUBLIC_DEMO = "false";
  try { assert.throws(() => issuePublicDemo({ tier: "FAMILY_PRO" }), /not enabled/); }
  finally { process.env.VOXA_PUBLIC_DEMO = "true"; }
});
