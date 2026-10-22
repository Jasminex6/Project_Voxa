import Stripe from "stripe";
import { DomainError, fulfillOrder } from "./commerce";
import { price } from "./catalog";
import { appUrl } from "./http";
import { transaction, type Order } from "./store";
import { notifyPaidOrder } from "./email";

export function stripeClient(): Stripe {
  if (!process.env.STRIPE_SECRET_KEY)
    throw new DomainError("Stripe is not configured", 503);
  return new Stripe(process.env.STRIPE_SECRET_KEY);
}
export async function createStripeCheckout(
  order: Order,
  email: string,
  lang: string,
) {
  if (!process.env.STRIPE_WEBHOOK_SECRET)
    throw new DomainError("Stripe webhooks are not configured", 503);
  const stripe = stripeClient();
  const priceId =
    process.env[
      `STRIPE_PRICE_${order.tier}_${order.currency}_${order.interval.toUpperCase()}`
    ];
  if (!priceId) throw new DomainError("This price is not available yet", 503);
  const configured = await stripe.prices.retrieve(priceId);
  if (
    !configured.active ||
    configured.unit_amount !==
      price(order.tier, order.currency, order.interval) ||
    configured.currency !== order.currency.toLowerCase() ||
    configured.recurring?.interval !==
      (order.interval === "annual" ? "year" : "month")
  )
    throw new DomainError("Merchant price does not match the catalog", 503);
  const session = await stripe.checkout.sessions.create(
    {
      mode: "subscription",
      customer_email: email,
      client_reference_id: order.id,
      metadata: { order_id: order.id },
      subscription_data: { metadata: { order_id: order.id } },
      line_items: [{ price: priceId, quantity: 1 }],
      success_url: `${appUrl()}/${lang}/checkout/success?order=${order.id}`,
      cancel_url: `${appUrl()}/${lang}/pricing?canceled=1`,
    },
    { idempotencyKey: order.id },
  );
  if (!session.url) throw new DomainError("Could not open checkout", 502);
  return { url: session.url, id: session.id };
}
export async function fulfillStripeSession(id: string, eventId: string) {
  const stripe = stripeClient();
  const session = await stripe.checkout.sessions.retrieve(id);
  if (
    session.mode !== "subscription" ||
    session.payment_status !== "paid" ||
    !session.subscription ||
    !session.client_reference_id
  )
    return;
  const subscription = await stripe.subscriptions.retrieve(
    typeof session.subscription === "string"
      ? session.subscription
      : session.subscription.id,
  );
  if (subscription.status !== "active") return;
  const expiresAt =
    Math.min(...subscription.items.data.map((i) => i.current_period_end)) *
    1000;
  await transaction((store) =>
    fulfillOrder(store, session.client_reference_id!, {
      gateway: "stripe",
      gatewayId: session.id,
      eventId,
      amount: session.amount_total ?? -1,
      currency: session.currency?.toUpperCase() ?? "",
      expiresAt,
      subscriptionId: subscription.id,
      customerId:
        typeof subscription.customer === "string"
          ? subscription.customer
          : subscription.customer.id,
    }),
  );
  await notifyPaidOrder(session.client_reference_id);
}
export async function refreshStripeSubscription(id: string) {
  const stripe = stripeClient();
  const latest = await stripe.subscriptions.retrieve(id);
  const latestInvoiceId =
    typeof latest.latest_invoice === "string"
      ? latest.latest_invoice
      : latest.latest_invoice?.id;
  const invoice = latestInvoiceId
    ? await stripe.invoices.retrieve(latestInvoiceId)
    : null;
  const isPaid = invoice?.status === "paid";
  await transaction(async (store) => {
    const subscription = (await store.all("subscriptions")).find(
      (s) => s.gatewaySubscriptionId === id,
    );
    if (!subscription) return;
    if (subscription.status === "revoked") return; // A later/stale event cannot undo a refund.
    const revoked =
      latest.status === "unpaid" || latest.status === "incomplete_expired";
    const status = revoked
      ? "revoked"
      : latest.status === "canceled" || latest.cancel_at_period_end
        ? "canceled"
        : latest.status === "active" && isPaid
          ? "active"
          : "past_due";
    await store.put("subscriptions", {
      ...subscription,
      status,
      currentPeriodEnd: isPaid
        ? Math.min(...latest.items.data.map((i) => i.current_period_end)) * 1000
        : subscription.currentPeriodEnd,
      updatedAt: Date.now(),
    });
    if (revoked)
      for (const license of await store.all("licenses"))
        if (license.subscriptionId === subscription.id)
          await store.put("licenses", { ...license, isRevoked: true });
  });
}

export async function revokeRefundedStripeCharge(chargeId: string) {
  const stripe = stripeClient(),
    charge = await stripe.charges.retrieve(chargeId);
  if (!charge.refunded || !charge.payment_intent) return; // Partial refunds do not silently revoke a plan.
  const paymentIntent =
    typeof charge.payment_intent === "string"
      ? charge.payment_intent
      : charge.payment_intent.id;
  const payments = stripe.invoicePayments.list({
    payment: { type: "payment_intent", payment_intent: paymentIntent },
    limit: 100,
  });
  for await (const payment of payments) {
    const invoiceId =
      typeof payment.invoice === "string"
        ? payment.invoice
        : payment.invoice.id;
    const invoice = await stripe.invoices.retrieve(invoiceId);
    const ref = invoice.parent?.subscription_details?.subscription;
    if (!ref) continue;
    const subscriptionId = typeof ref === "string" ? ref : ref.id;
    const remote = await stripe.subscriptions.retrieve(subscriptionId);
    await transaction(async (store) => {
      const subscription = (await store.all("subscriptions")).find(
        (s) => s.gatewaySubscriptionId === subscriptionId,
      );
      const order = (await store.all("orders")).find(
        (o) => o.id === (subscription?.orderId ?? remote.metadata.order_id),
      );
      if (order) await store.put("orders", { ...order, status: "refunded" });
      if (!subscription) return;
      await store.put("subscriptions", {
        ...subscription,
        status: "revoked",
        updatedAt: Date.now(),
      });
      for (const license of await store.all("licenses"))
        if (license.subscriptionId === subscription.id)
          await store.put("licenses", { ...license, isRevoked: true });
    });
  }
}
