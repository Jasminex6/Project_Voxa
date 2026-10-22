import test from "node:test";
import assert from "node:assert/strict";
import { createHmac, generateKeyPairSync, randomUUID } from "node:crypto";
import { addPeriod } from "../lib/catalog";
import {
  createExchange,
  fulfillOrder,
  ownedOrders,
  redeemExchange,
} from "../lib/commerce";
import { MemoryStore, type Order } from "../lib/store";
import { paymobMessage, verifyPaymob } from "../lib/paymob";
const pair = generateKeyPairSync("ed25519");
process.env.VOXA_LICENSE_KEY_ID = "test-v1";
process.env.VOXA_LICENSE_PRIVATE_KEY = pair.privateKey
  .export({ type: "pkcs8", format: "pem" })
  .toString();
const order = (): Order => ({
  id: randomUUID(),
  userId: randomUUID(),
  tier: "FAMILY_PRO",
  currency: "EGP",
  interval: "monthly",
  amount: 25000,
  gateway: "demo",
  gatewayId: "demo-order",
  status: "pending",
  createdAt: Date.now(),
});
const details = {
  gateway: "demo",
  gatewayId: "demo-order",
  eventId: "evt-1",
  amount: 25000,
  currency: "EGP",
};
async function paid() {
  const store = new MemoryStore(),
    o = order();
  await store.put("orders", o);
  const license = await fulfillOrder(store, o.id, details);
  return { store, o, license };
}
test("duplicate webhook fulfillment creates only one subscription and license", async () => {
  const { store, o, license } = await paid();
  const second = await fulfillOrder(store, o.id, details);
  assert.equal(second.id, license.id);
  assert.equal((await store.all("licenses")).length, 1);
  assert.equal((await store.all("subscriptions")).length, 1);
});
test("untrusted amounts, currencies, providers, and transaction IDs never unlock", async () => {
  for (const changed of [
    { amount: 1 },
    { currency: "USD" },
    { gateway: "stripe" },
    { gatewayId: "other" },
  ]) {
    const store = new MemoryStore(),
      o = order();
    await store.put("orders", o);
    await assert.rejects(
      () => fulfillOrder(store, o.id, { ...details, ...changed }),
      /does not match/,
    );
    assert.equal((await store.all("licenses")).length, 0);
  }
});
test("order ownership is checked and pending orders have no entitlement", async () => {
  const store = new MemoryStore(),
    o = order();
  await store.put("orders", o);
  assert.equal((await ownedOrders(store, randomUUID())).length, 0);
  assert.equal((await store.all("licenses")).length, 0);
  await assert.rejects(
    () => createExchange(store, o.userId, randomUUID()),
    /unavailable/,
  );
});
test("activation pairs once, retries on the same device, and respects seat limit", async () => {
  const { store, o, license } = await paid();
  const exchange = await createExchange(store, o.userId, license.id),
    device = randomUUID();
  assert.ok((await redeemExchange(store, exchange.code, device)).token);
  await redeemExchange(store, exchange.code, device);
  assert.equal((await store.all("device_activations")).length, 1);
  await assert.rejects(
    () => redeemExchange(store, exchange.code, randomUUID()),
    /already paired/,
  );
  const second = await createExchange(store, o.userId, license.id);
  await redeemExchange(store, second.code, randomUUID());
  const third = await createExchange(store, o.userId, license.id);
  await assert.rejects(
    () => redeemExchange(store, third.code, randomUUID()),
    /limit/,
  );
});
test("another account, expired exchanges, and revoked licenses cannot activate", async () => {
  const { store, o, license } = await paid();
  await assert.rejects(
    () => createExchange(store, randomUUID(), license.id),
    /unavailable/,
  );
  const exchange = await createExchange(store, o.userId, license.id);
  await assert.rejects(
    () =>
      redeemExchange(
        store,
        exchange.code,
        randomUUID(),
        exchange.expiresAt + 1,
      ),
    /expired/,
  );
  await store.put("licenses", { ...license, isRevoked: true });
  await assert.rejects(
    () => redeemExchange(store, exchange.code, randomUUID()),
    /unavailable/,
  );
});
test("missing signer does not consume a device seat or exchange", async () => {
  const { store, o, license } = await paid(),
    exchange = await createExchange(store, o.userId, license.id);
  const saved = process.env.VOXA_LICENSE_PRIVATE_KEY;
  delete process.env.VOXA_LICENSE_PRIVATE_KEY;
  try {
    await assert.rejects(
      () => redeemExchange(store, exchange.code, randomUUID()),
      /configured/,
    );
    assert.equal((await store.all("device_activations")).length, 0);
    assert.equal((await store.all("activation_exchanges"))[0].redeemedAt, null);
  } finally {
    process.env.VOXA_LICENSE_PRIVATE_KEY = saved;
  }
});
test("calendar periods clamp month ends and leap days", () => {
  assert.equal(
    new Date(addPeriod(Date.UTC(2026, 0, 31), "monthly")).toISOString(),
    "2026-02-28T00:00:00.000Z",
  );
  assert.equal(
    new Date(addPeriod(Date.UTC(2028, 1, 29), "annual")).toISOString(),
    "2029-02-28T00:00:00.000Z",
  );
});
test("past-due renewal cannot issue new paid access", async () => {
  const { store, o, license } = await paid();
  const exchange = await createExchange(store, o.userId, license.id);
  const sub = (await store.all("subscriptions"))[0];
  await store.put("subscriptions", { ...sub, status: "past_due" });
  await assert.rejects(
    () => createExchange(store, o.userId, license.id),
    /Renew/,
  );
  await assert.rejects(
    () => redeemExchange(store, exchange.code, randomUUID()),
    /Renew/,
  );
});
test("Paymob callback concatenation matches the official worked example", () => {
  const obj = {
    amount_cents: 100,
    created_at: "2020-03-25T18:39:44.719228",
    currency: "EGP",
    error_occured: false,
    has_parent_transaction: false,
    id: 2556706,
    integration_id: 6741,
    is_3d_secure: true,
    is_auth: false,
    is_capture: false,
    is_refunded: false,
    is_standalone_payment: true,
    is_voided: false,
    order: { id: 4778239 },
    owner: 4705,
    pending: false,
    source_data: { pan: 2346, sub_type: "MasterCard", type: "card" },
    success: true,
  };
  const expected =
    "1002020-03-25T18:39:44.719228EGPfalsefalse25567066741truefalsefalsefalsetruefalse47782394705false2346MasterCardcardtrue";
  assert.equal(paymobMessage(obj), expected);
  const signature = createHmac("sha512", "test-secret")
    .update(expected)
    .digest("hex");
  assert.equal(verifyPaymob(obj, signature, "test-secret"), true);
  assert.equal(
    verifyPaymob({ ...obj, amount_cents: 1 }, signature, "test-secret"),
    false,
  );
  assert.equal(verifyPaymob(obj, "", "test-secret"), false);
});
