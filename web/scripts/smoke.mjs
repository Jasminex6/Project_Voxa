import assert from "node:assert/strict";
import { createPublicKey, randomUUID, verify } from "node:crypto";
import { readFileSync } from "node:fs";
process.loadEnvFile(".env.local");
const base = process.env.APP_URL;
assert.equal(
  process.env.VOXA_DEMO_MODE,
  "true",
  "Smoke test requires the local demo",
);
assert.ok(["127.0.0.1", "localhost"].includes(new URL(base).hostname));
let cookie = "";
async function request(path, body, expected = 200, customCookie = cookie) {
  const result = await fetch(`${base}/api/${path}`, {
    method: body === undefined ? "GET" : "POST",
    headers: {
      "Content-Type": "application/json",
      Origin: base,
      Cookie: customCookie,
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  assert.equal(result.status, expected, `${path}: ${result.status}`);
  return {
    data: await result.json(),
    cookie: result.headers.get("set-cookie")?.split(";")[0],
  };
}
await request("dashboard", undefined, 401);
cookie = (await request("auth/demo", {})).cookie;
assert.ok(cookie);
await request(
  "checkout",
  {
    tier: "FAMILY_PRO",
    currency: "EGP",
    interval: "monthly",
    gateway: "demo",
    lang: "en",
    amount: 1,
  },
  400,
);
const checkout = (
  await request("checkout", {
    tier: "FAMILY_PRO",
    currency: "EGP",
    interval: "monthly",
    gateway: "demo",
    lang: "en",
  })
).data;
let order = (await request(`orders/${checkout.orderId}`)).data;
assert.equal(order.order.status, "pending");
assert.equal(order.order.amount, 34900);
assert.equal(order.license, undefined);
for (const [currency, interval, amount] of [
  ["EGP", "annual", 279900],
  ["SAR", "monthly", 9900],
  ["SAR", "annual", 89900],
]) {
  const result = (
    await request("checkout", {
      tier: "FAMILY_PRO",
      currency,
      interval,
      gateway: "demo",
      lang: "en",
    })
  ).data;
  const pending = (await request(`orders/${result.orderId}`)).data;
  assert.equal(pending.order.amount, amount);
  assert.equal(pending.order.currency, currency);
  assert.equal(pending.license, undefined);
}
await request(
  "checkout",
  {
    tier: "CLINICAL_SLP",
    currency: "EGP",
    interval: "annual",
    gateway: "demo",
    lang: "en",
  },
  400,
);
await request("checkout/demo/complete", { orderId: checkout.orderId });
await request("checkout/demo/complete", { orderId: checkout.orderId });
order = (await request(`orders/${checkout.orderId}`)).data;
assert.equal(order.order.status, "paid");
assert.ok(order.license.id);
const exchange = (
  await request("license/exchange", { licenseId: order.license.id })
).data;
const deviceId = randomUUID();
const activation = (
  await request("license/activate", { code: exchange.code, deviceId })
).data;
const parts = activation.token.split("."),
  header = JSON.parse(Buffer.from(parts[0], "base64url").toString());
const keys = JSON.parse(process.env.VOXA_LICENSE_PUBLIC_KEYS);
assert.ok(
  verify(
    null,
    Buffer.from(parts.slice(0, 2).join(".")),
    createPublicKey(keys[header.kid]),
    Buffer.from(parts[2], "base64url"),
  ),
);
assert.equal(
  JSON.parse(Buffer.from(parts[1], "base64url").toString()).device_id,
  deviceId,
);
await request(
  "license/activate",
  { code: exchange.code, deviceId: randomUUID() },
  403,
);
const otherCookie = (await request("auth/demo", {}, 200, "")).cookie;
await request(`orders/${checkout.orderId}`, undefined, 404, otherCookie);
await request(
  "license/exchange",
  { licenseId: order.license.id },
  404,
  otherCookie,
);
await request("demo/cancel", { subscriptionId: order.subscription.id });
const dashboard = (await request("dashboard")).data;
assert.equal(dashboard.licenses.length, 1);
assert.equal(dashboard.devices.length, 1);
assert.equal(dashboard.subscriptions[0].status, "canceled");
assert.ok(dashboard.subscriptions[0].currentPeriodEnd > Date.now());
const report = {
  schemaVersion: 1,
  reportId: randomUUID(),
  subjectId: randomUUID(),
  generatedAt: new Date().toISOString(),
  periodStart: "2026-10-21T10:00:00Z",
  periodEnd: "2026-10-21T10:00:00Z",
  measure: "speech_recognition_text_match",
  historyCaveat:
    "Older app versions recorded successful attempts only. Scores are not clinical measurements. Task counts are current status, not weekly completion history.",
  practice: {
    recordedAttempts: 2,
    activeDays: 1,
    meanMatchScore: 75,
    daily: [{ date: "2026-10-21", recordedAttempts: 2, meanMatchScore: 75 }],
  },
  tasks: { total: 1, approved: 1 },
};
await request("reports", { ...report, recordings: ["not-allowed"] }, 400);
await request("reports", report);
await request("reports", report);
assert.equal((await request("reports")).data.length, 1);
assert.equal(
  (await request("reports", undefined, 200, otherCookie)).data.length,
  0,
);
const review = JSON.parse(
  readFileSync(
    new URL("../../config/progress-report-example.json", import.meta.url),
    "utf8",
  ),
);
review.reportId = randomUUID();
review.subjectId = randomUUID();
await request("reports", { ...review, transcript: "not-allowed" }, 400);
const inconsistent = structuredClone(review);
inconsistent.tasks.support.independent++;
await request("reports", inconsistent, 400);
await request("reports", review);
await request("reports", review);
const uploaded = (await request("reports")).data;
assert.equal(uploaded.length, 2);
assert.ok(
  uploaded.some(
    (x) =>
      x.summary.schemaVersion === 2 &&
      x.summary.practice.current.matched === 30,
  ),
);
assert.equal(
  (await request("reports", undefined, 200, otherCookie)).data.length,
  0,
);
await request("webhooks/stripe", {}, 503);
await request("webhooks/paymob", {}, 503);
console.log(
  "PASS: pending order, price tampering, duplicate payment, signatures, device binding, ownership, cancellation, report allowlist/deduplication/isolation, disabled live webhooks.",
);
