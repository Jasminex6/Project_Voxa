import { randomUUID } from "node:crypto";
import { z, ZodError } from "zod";
import { catalog, price } from "./catalog";
import {
  createExchange,
  DomainError,
  fulfillOrder,
  ownedOrders,
  redeemExchange,
} from "./commerce";
import {
  appUrl,
  assertLocalDemo,
  authenticate,
  bodyText,
  checkOrigin,
  demoCookie,
  jsonBody,
  rateLimit,
  response,
} from "./http";
import { createPaymobCheckout, verifyPaymob } from "./paymob";
import {
  createStripeCheckout,
  fulfillStripeSession,
  refreshStripeSubscription,
  revokeRefundedStripeCharge,
  stripeClient,
} from "./stripe";
import { demoMode, transaction, type Order } from "./store";
import { reportSchema } from "./report";
import { notifyPaidOrder } from "./email";

const checkoutSchema = z
  .object({
    tier: z.enum(["FAMILY_PRO", "CLINICAL_SLP"]),
    currency: z.enum(["EGP", "SAR"]),
    interval: z.enum(["monthly", "annual"]),
    gateway: z.enum(["demo", "stripe", "paymob"]),
    lang: z.enum(["en", "ar"]).default("en"),
    name: z.string().trim().min(2).max(100).optional(),
    phone: z
      .string()
      .regex(/^\+?[0-9]{9,15}$/)
      .optional(),
  })
  .strict()
  .refine((input) => input.tier !== "CLINICAL_SLP" || input.interval === "monthly", {
    message: "The clinic plan is billed monthly only",
  });
const uuid = z.string().uuid();
const activateSchema = z
  .object({ code: z.string().regex(/^[A-Za-z0-9_-]{32,128}$/), deviceId: uuid })
  .strict();

export async function handleApi(
  request: Request,
  path: string[],
): Promise<Response> {
  try {
    const route = path.join("/");
    if (route === "webhooks/stripe" && request.method === "POST")
      return await stripeWebhook(request);
    if (route === "webhooks/paymob" && request.method === "POST")
      return await paymobWebhook(request);
    if (request.method === "GET" && route === "configuration")
      return response({
        demo: demoMode(),
        prices: catalog,
        stripe:
          !!process.env.STRIPE_SECRET_KEY &&
          !!process.env.STRIPE_WEBHOOK_SECRET,
        paymob:
          !!process.env.PAYMOB_SECRET_KEY && !!process.env.PAYMOB_HMAC_SECRET,
        authConfigured: !!process.env.NEXT_PUBLIC_SUPABASE_URL,
      });
    if (route === "license/activate" && request.method === "POST") {
      if (demoMode()) assertLocalDemo(request);
      rateLimit(
        "activate:" +
          (request.headers.get("x-forwarded-for")?.split(",")[0] ?? "local"),
        20,
      );
      const input = activateSchema.parse(await jsonBody(request));
      return response(
        await transaction((store) =>
          redeemExchange(store, input.code, input.deviceId),
        ),
      );
    }
    if (request.method !== "GET") checkOrigin(request);
    if (route === "auth/demo" && request.method === "POST") {
      assertLocalDemo(request);
      try {
        return response({ user: await authenticate(request) });
      } catch {
        /* create a new local-only session */
      }
      return new Response(JSON.stringify({ ok: true }), {
        headers: {
          "Content-Type": "application/json",
          "Set-Cookie": demoCookie(),
          "Cache-Control": "no-store",
        },
      });
    }
    if (route === "auth/logout" && request.method === "POST")
      return new Response(null, {
        status: 204,
        headers: {
          "Set-Cookie":
            "voxa_demo=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0",
        },
      });

    const user = await authenticate(request);
    rateLimit(
      `${user.id}:${route.split("/")[0]}`,
      request.method === "GET" ? 120 : 20,
    );
    if (route === "session" && request.method === "GET")
      return response({ user });
    if (route === "checkout" && request.method === "POST") {
      const input = checkoutSchema.parse(await jsonBody(request));
      if (input.gateway === "demo") assertLocalDemo(request);
      if (input.gateway !== "demo" && demoMode())
        throw new DomainError("Live payments are disabled in demo mode", 403);
      if (input.gateway === "paymob" && (!input.name || !input.phone))
        throw new DomainError(
          "Paymob requires your billing name and phone number",
        );
      const order: Order = {
        id: randomUUID(),
        userId: user.id,
        tier: input.tier,
        currency: input.currency,
        interval: input.interval,
        amount: price(input.tier, input.currency, input.interval),
        gateway: input.gateway,
        status: "pending",
        gatewayId: null,
        createdAt: Date.now(),
        billingEmail: user.email,
      };
      await transaction((store) => store.put("orders", order));
      const checkout =
        input.gateway === "demo"
          ? {
              id: order.id,
              url: `${appUrl()}/${input.lang}/checkout/demo?order=${order.id}`,
            }
          : input.gateway === "stripe"
            ? await createStripeCheckout(order, user.email, input.lang)
            : await createPaymobCheckout(
                order,
                user.email,
                input.phone!,
                input.name!,
                input.lang,
              );
      await transaction((store) =>
        store.put("orders", { ...order, gatewayId: checkout.id }),
      );
      return response({ url: checkout.url, orderId: order.id });
    }
    if (route === "checkout/demo/complete" && request.method === "POST") {
      assertLocalDemo(request);
      const { orderId } = z
        .object({ orderId: uuid })
        .strict()
        .parse(await jsonBody(request));
      return response(
        await transaction(async (store) => {
          const order = (await store.all("orders")).find(
            (o) => o.id === orderId && o.userId === user.id,
          );
          if (!order || order.gateway !== "demo")
            throw new DomainError("Order not found", 404);
          await fulfillOrder(store, order.id, {
            gateway: "demo",
            gatewayId: order.id,
            eventId: `demo-${order.id}`,
            amount: order.amount,
            currency: order.currency,
          });
          return { ok: true };
        }),
      );
    }
    if (route === "dashboard" && request.method === "GET")
      return response(
        await transaction(async (store) => {
          const licenses = (await store.all("licenses")).filter(
            (l) => l.userId === user.id,
          );
          const subscriptions = (await store.all("subscriptions")).filter(
            (s) => s.userId === user.id,
          );
          const devices = (await store.all("device_activations")).filter(
            (d) => d.userId === user.id,
          );
          return {
            user,
            demo: demoMode(),
            licenses,
            subscriptions,
            devices,
            orders: await ownedOrders(store, user.id),
          };
        }),
      );
    if (route === "reports" && request.method === "GET")
      return response(
        await transaction(async (store) =>
          (await store.all("reports"))
            .filter((r) => r.userId === user.id)
            .sort((a, b) => b.generatedAt - a.generatedAt),
        ),
      );
    if (route === "reports" && request.method === "POST") {
      const summary = reportSchema.parse(await jsonBody(request));
      return response(
        await transaction(async (store) => {
          const active = (await store.all("subscriptions")).some(
            (s) =>
              s.userId === user.id &&
              s.status !== "revoked" &&
              s.currentPeriodEnd > Date.now(),
          );
          if (!active)
            throw new DomainError(
              "An active paid plan is required to store reports",
              403,
            );
          const existing = (await store.all("reports")).find(
            (r) => r.userId === user.id && r.reportId === summary.reportId,
          );
          if (existing) return { id: existing.id };
          const id = randomUUID();
          await store.put("reports", {
            id,
            userId: user.id,
            subjectId: summary.subjectId,
            reportId: summary.reportId,
            generatedAt: Date.parse(summary.generatedAt),
            summary,
          });
          return { id };
        }),
      );
    }
    if (path[0] === "orders" && path.length === 2 && request.method === "GET") {
      const id = uuid.parse(path[1]);
      return response(
        await transaction(async (store) => {
          const order = (await store.all("orders")).find(
            (o) => o.id === id && o.userId === user.id,
          );
          if (!order) throw new DomainError("Order not found", 404);
          const subscription = (await store.all("subscriptions")).find(
            (s) => s.orderId === id && s.userId === user.id,
          );
          const license = (await store.all("licenses")).find(
            (l) =>
              l.subscriptionId === subscription?.id && l.userId === user.id,
          );
          return { order, license, subscription };
        }),
      );
    }
    if (route === "license/exchange" && request.method === "POST") {
      const { licenseId } = z
        .object({ licenseId: uuid })
        .strict()
        .parse(await jsonBody(request));
      return response(
        await transaction((store) => createExchange(store, user.id, licenseId)),
      );
    }
    if (route === "billing/portal" && request.method === "POST") {
      const { subscriptionId } = z
        .object({ subscriptionId: uuid })
        .strict()
        .parse(await jsonBody(request));
      const sub = await transaction(async (store) =>
        (await store.all("subscriptions")).find(
          (s) => s.id === subscriptionId && s.userId === user.id,
        ),
      );
      if (!sub?.gatewayCustomerId || sub.gateway !== "stripe")
        throw new DomainError(
          "Contact support to manage this prepaid plan",
          409,
        );
      const portal = await stripeClient().billingPortal.sessions.create({
        customer: sub.gatewayCustomerId,
        return_url: `${appUrl()}/en/dashboard`,
      });
      return response({ url: portal.url });
    }
    if (route === "demo/cancel" && request.method === "POST") {
      assertLocalDemo(request);
      const { subscriptionId } = z
        .object({ subscriptionId: uuid })
        .strict()
        .parse(await jsonBody(request));
      await transaction(async (store) => {
        const sub = (await store.all("subscriptions")).find(
          (s) =>
            s.id === subscriptionId &&
            s.userId === user.id &&
            s.gateway === "demo",
        );
        if (!sub) throw new DomainError("Subscription not found", 404);
        await store.put("subscriptions", {
          ...sub,
          status: "canceled",
          updatedAt: Date.now(),
        });
      });
      return response({ ok: true });
    }
    throw new DomainError("Not found", 404);
  } catch (error) {
    if (error instanceof DomainError)
      return response({ error: error.message }, error.status);
    if (error instanceof ZodError)
      return response(
        {
          error: "Check the required fields",
          fields: error.issues.map((i) => i.path.join(".")),
        },
        400,
      );
    // Never expose raw gateway bodies, credentials, activation codes, or tokens in logs.
    console.error("Voxa API request failed", {
      path: path.join("/"),
      kind: error instanceof Error ? error.name : "Unknown",
    });
    return response(
      {
        error: "The service is not ready. Please try again or contact support.",
      },
      503,
    );
  }
}

async function stripeWebhook(request: Request) {
  const secret = process.env.STRIPE_WEBHOOK_SECRET;
  if (!secret || demoMode())
    throw new DomainError("Stripe webhooks are not configured", 503);
  const raw = await bodyText(request, 1_048_576);
  let event;
  try {
    event = stripeClient().webhooks.constructEvent(
      raw,
      request.headers.get("stripe-signature") ?? "",
      secret,
    );
  } catch {
    throw new DomainError("Invalid webhook signature", 401);
  }
  if (
    event.type === "checkout.session.completed" ||
    event.type === "checkout.session.async_payment_succeeded"
  ) {
    await fulfillStripeSession(event.data.object.id, event.id);
  } else if (
    event.type === "customer.subscription.updated" ||
    event.type === "customer.subscription.deleted"
  ) {
    await refreshStripeSubscription(event.data.object.id);
  } else if (
    event.type === "invoice.paid" ||
    event.type === "invoice.payment_failed"
  ) {
    const invoice = event.data.object;
    const subscription = invoice.parent?.subscription_details?.subscription;
    if (subscription)
      await refreshStripeSubscription(
        typeof subscription === "string" ? subscription : subscription.id,
      );
  } else if (event.type === "charge.refunded") {
    await revokeRefundedStripeCharge(event.data.object.id);
  }
  return response({ received: true });
}
async function paymobWebhook(request: Request) {
  const secret = process.env.PAYMOB_HMAC_SECRET;
  if (!secret || demoMode())
    throw new DomainError("Paymob webhooks are not configured", 503);
  const body = z
    .object({
      type: z.literal("TRANSACTION"),
      obj: z.record(z.string(), z.unknown()),
    })
    .parse(await jsonBody(request));
  const object = body.obj;
  if (
    !verifyPaymob(
      object,
      new URL(request.url).searchParams.get("hmac") ?? "",
      secret,
    )
  )
    throw new DomainError("Invalid webhook signature", 401);
  const orderId = String((object.order as Record<string, unknown>)?.id ?? "");
  if (object.is_refunded === true || object.is_voided === true) {
    await transaction(async (store) => {
      const order = (await store.all("orders")).find(
        (o) => o.gateway === "paymob" && o.gatewayId === orderId,
      );
      if (!order) throw new DomainError("Order is not ready", 409);
      await store.put("orders", { ...order, status: "refunded" });
      const sub = (await store.all("subscriptions")).find(
        (s) => s.orderId === order.id,
      );
      if (sub) {
        await store.put("subscriptions", {
          ...sub,
          status: "revoked",
          updatedAt: Date.now(),
        });
        for (const license of await store.all("licenses"))
          if (license.subscriptionId === sub.id)
            await store.put("licenses", { ...license, isRevoked: true });
      }
    });
  } else if (
    object.success === true &&
    object.pending === false &&
    object.error_occured === false &&
    object.is_auth === false
  ) {
    const fulfilledOrderId = await transaction(async (store) => {
      const order = (await store.all("orders")).find(
        (o) => o.gateway === "paymob" && o.gatewayId === orderId,
      );
      if (!order) throw new DomainError("Order is not ready", 409);
      await fulfillOrder(store, order.id, {
        gateway: "paymob",
        gatewayId: orderId,
        eventId: String(object.id),
        amount: Number(object.amount_cents),
        currency: String(object.currency),
      });
      return order.id;
    });
    await notifyPaidOrder(fulfilledOrderId);
  }
  return response({ received: true });
}
