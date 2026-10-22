import test from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, randomUUID, sign } from "node:crypto";
import { readFileSync } from "node:fs";
import { signLicense, verifyLicense } from "../lib/license";

const pair = generateKeyPairSync("ed25519");
const privateKey = pair.privateKey
  .export({ type: "pkcs8", format: "pem" })
  .toString();
const publicKey = pair.publicKey
  .export({ type: "spki", format: "pem" })
  .toString();
process.env.VOXA_LICENSE_KEY_ID = "test-v1";
process.env.VOXA_LICENSE_PRIVATE_KEY = privateKey;
const now = Date.now(),
  device = randomUUID(),
  user = randomUUID();
const issue = () =>
  signLicense(
    {
      userId: user,
      deviceId: device,
      licenseKey: "VOXA-1111-2222-3333-4444",
      tier: "FAMILY_PRO",
      devices: 2,
      expiresAt: now + 86400_000,
    },
    now,
  );
const keys = { "test-v1": publicKey };
test("server-issued license verifies for its installation", () => {
  const result = verifyLicense(issue(), keys, device, now);
  assert.equal(result.tier, "FAMILY_PRO");
  assert.equal(result.sub, user);
});
test("changing the payload cannot upgrade the tier", () => {
  const parts = issue().split(".");
  parts[1] = Buffer.from(
    Buffer.from(parts[1], "base64url")
      .toString()
      .replace("FAMILY_PRO", "CLINICAL_SLP"),
  ).toString("base64url");
  assert.throws(
    () => verifyLicense(parts.join("."), keys, device, now),
    /signature/,
  );
});
test("another installation and unknown key are rejected", () => {
  assert.throws(
    () => verifyLicense(issue(), keys, randomUUID(), now),
    /mismatched/,
  );
  assert.throws(() => verifyLicense(issue(), {}, device, now), /Untrusted/);
});
test("signed paid-through and grace boundaries are enforced", () => {
  const token = issue(),
    claims = verifyLicense(token, keys, device, now);
  assert.doesNotThrow(() =>
    verifyLicense(token, keys, device, claims.exp * 1000 + 1000),
  );
  assert.throws(
    () => verifyLicense(token, keys, device, (claims.grace_until + 1) * 1000),
    /Expired/,
  );
  assert.throws(() => verifyLicense(token, keys, device, claims.grace_until * 1000), /Expired/);
  assert.throws(
    () => verifyLicense(token, keys, device, now - 1000_000),
    /Expired/,
  );
});
test("alg:none and a substituted signature fail closed", () => {
  const parts = issue().split(".");
  parts[0] = Buffer.from(
    JSON.stringify({ alg: "none", kid: "test-v1", typ: "JWT" }),
  ).toString("base64url");
  assert.throws(
    () => verifyLicense(parts.join("."), keys, device, now),
    /Untrusted/,
  );
  parts[0] = Buffer.from(
    JSON.stringify({ alg: "EdDSA", kid: "test-v1", typ: "JWT" }),
  ).toString("base64url");
  parts[2] = Buffer.alloc(64).toString("base64url");
  assert.throws(
    () => verifyLicense(parts.join("."), keys, device, now),
    /signature/,
  );
});
test("invalid signed grace duration is rejected", () => {
  const parts = issue().split("."),
    claims = JSON.parse(Buffer.from(parts[1], "base64url").toString());
  claims.grace_until += 86400;
  parts[1] = Buffer.from(JSON.stringify(claims)).toString("base64url");
  parts[2] = sign(
    null,
    Buffer.from(parts.slice(0, 2).join(".")),
    pair.privateKey,
  ).toString("base64url");
  assert.throws(
    () => verifyLicense(parts.join("."), keys, device, now),
    /Expired/,
  );
});
test("shared fixture consumed by Android uses the same JWS schema", () => {
  const fixture = JSON.parse(
    readFileSync(
      new URL("../../config/license-test-fixture.json", import.meta.url),
      "utf8",
    ),
  );
  const prefix = Buffer.from("302a300506032b6570032100", "hex");
  const der = Buffer.concat([prefix, Buffer.from(fixture.publicKey, "base64")]);
  const pem = `-----BEGIN PUBLIC KEY-----\n${der.toString("base64")}\n-----END PUBLIC KEY-----`;
  assert.equal(
    verifyLicense(
      fixture.token,
      { "demo-v1": pem },
      fixture.deviceId,
      fixture.nowSeconds * 1000,
    ).tier,
    "FAMILY_PRO",
  );
});
