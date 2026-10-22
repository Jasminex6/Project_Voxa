import { createHash, randomBytes, randomUUID } from "node:crypto";
import { addPeriod, catalog } from "./catalog";
import { newLicenseKey, signLicense } from "./license";
import type { License, Order, Store } from "./store";

export class DomainError extends Error {
  constructor(
    message: string,
    readonly status = 400,
  ) {
    super(message);
  }
}
export async function fulfillOrder(
  store: Store,
  orderId: string,
  details: {
    eventId: string;
    gateway: string;
    gatewayId: string;
    amount: number;
    currency: string;
    expiresAt?: number;
    subscriptionId?: string;
    customerId?: string;
  },
  now = Date.now(),
): Promise<License> {
  const order = (await store.all("orders")).find((o) => o.id === orderId);
  if (
    !order ||
    order.gateway !== details.gateway ||
    order.gatewayId !== details.gatewayId ||
    order.amount !== details.amount ||
    order.currency !== details.currency
  )
    throw new DomainError("Payment does not match the order");
  const priorSub = (await store.all("subscriptions")).find(
    (s) => s.orderId === order.id,
  );
  if (priorSub) {
    const priorLicense = (await store.all("licenses")).find(
      (l) => l.subscriptionId === priorSub.id,
    );
    if (!priorLicense) throw new Error("Incomplete fulfillment");
    return priorLicense;
  }
  if (order.status !== "pending")
    throw new DomainError("Order cannot be fulfilled");
  const expiresAt = details.expiresAt ?? addPeriod(now, order.interval);
  if (expiresAt <= now) throw new DomainError("Payment has no active period");
  const subscriptionId = randomUUID();
  await store.put("subscriptions", {
    id: subscriptionId,
    userId: order.userId,
    orderId,
    gateway: details.gateway,
    gatewaySubscriptionId: details.subscriptionId ?? null,
    gatewayCustomerId: details.customerId ?? null,
    tier: order.tier,
    status: "active",
    currentPeriodEnd: expiresAt,
    updatedAt: now,
  });
  const license: License = {
    id: randomUUID(),
    userId: order.userId,
    subscriptionId,
    licenseKey: newLicenseKey(),
    tier: order.tier,
    maxDevices: catalog[order.tier].devices,
    isRevoked: false,
    createdAt: now,
  };
  await store.put("licenses", license);
  await store.put("orders", { ...order, status: "paid" });
  await store.put("webhook_events", {
    id: randomUUID(),
    userId: order.userId,
    gateway: details.gateway,
    gatewayEventId: details.eventId,
    processedAt: now,
  });
  return license;
}
export const codeHash = (code: string): string =>
  createHash("sha256").update(code).digest("hex");
export async function createExchange(
  store: Store,
  userId: string,
  licenseId: string,
  now = Date.now(),
) {
  const license = (await store.all("licenses")).find(
    (l) => l.id === licenseId && l.userId === userId,
  );
  if (!license || license.isRevoked)
    throw new DomainError("License is unavailable", 404);
  const sub = (await store.all("subscriptions")).find(
    (s) => s.id === license.subscriptionId,
  );
  if (
    !sub ||
    !["active", "canceled"].includes(sub.status) ||
    sub.currentPeriodEnd < now
  )
    throw new DomainError("Renew before activating another device", 409);
  const code = randomBytes(32).toString("base64url");
  await store.put("activation_exchanges", {
    id: randomUUID(),
    userId,
    licenseId,
    codeHash: codeHash(code),
    expiresAt: now + 15 * 60_000,
    deviceId: null,
    redeemedAt: null,
  });
  return { code, expiresAt: now + 15 * 60_000 };
}
export async function redeemExchange(
  store: Store,
  code: string,
  deviceId: string,
  now = Date.now(),
) {
  const exchange = (await store.all("activation_exchanges")).find(
    (e) => e.codeHash === codeHash(code),
  );
  if (
    !exchange ||
    exchange.expiresAt < now ||
    (exchange.deviceId && exchange.deviceId !== deviceId)
  )
    throw new DomainError(
      "Activation code is invalid, expired, or already paired",
      403,
    );
  const license = (await store.all("licenses")).find(
    (l) => l.id === exchange.licenseId,
  );
  if (!license || license.isRevoked)
    throw new DomainError("License is unavailable", 403);
  const sub = (await store.all("subscriptions")).find(
    (s) => s.id === license.subscriptionId,
  );
  if (
    !sub ||
    !["active", "canceled"].includes(sub.status) ||
    sub.currentPeriodEnd <= now
  )
    throw new DomainError("Renew the subscription first", 403);
  const devices = (await store.all("device_activations")).filter(
    (d) => d.licenseId === license.id,
  );
  const existing = devices.find((d) => d.deviceId === deviceId);
  if (!existing && devices.length >= license.maxDevices)
    throw new DomainError("Device limit reached", 409);
  // Sign before committing the seat: misconfigured signing never consumes an exchange.
  const token = signLicense(
    {
      userId: license.userId,
      licenseKey: license.licenseKey,
      tier: license.tier,
      deviceId,
      devices: license.maxDevices,
      expiresAt: sub.currentPeriodEnd,
    },
    now,
  );
  await store.put("device_activations", {
    id: existing?.id ?? randomUUID(),
    userId: license.userId,
    licenseId: license.id,
    deviceId,
    activatedAt: existing?.activatedAt ?? now,
    lastPingAt: now,
  });
  await store.put("activation_exchanges", {
    ...exchange,
    deviceId,
    redeemedAt: exchange.redeemedAt ?? now,
  });
  return { token, tier: license.tier, expiresAt: sub.currentPeriodEnd };
}
export async function ownedOrders(
  store: Store,
  userId: string,
): Promise<Order[]> {
  return (await store.all("orders"))
    .filter((o) => o.userId === userId)
    .sort((a, b) => b.createdAt - a.createdAt);
}
