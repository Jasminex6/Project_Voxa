import { createHmac, timingSafeEqual } from "node:crypto";
import { DomainError } from "./commerce";
import { appUrl } from "./http";
import type { Order } from "./store";

export const hmacFields = [
  "amount_cents",
  "created_at",
  "currency",
  "error_occured",
  "has_parent_transaction",
  "id",
  "integration_id",
  "is_3d_secure",
  "is_auth",
  "is_capture",
  "is_refunded",
  "is_standalone_payment",
  "is_voided",
  "order.id",
  "owner",
  "pending",
  "source_data.pan",
  "source_data.sub_type",
  "source_data.type",
  "success",
] as const;
export function paymobMessage(object: Record<string, unknown>): string {
  return hmacFields
    .map((path) => {
      let value: unknown = object;
      for (const part of path.split("."))
        value = (value as Record<string, unknown> | undefined)?.[part];
      if (!["string", "number", "boolean"].includes(typeof value))
        throw new DomainError("Incomplete payment callback", 401);
      return String(value);
    })
    .join("");
}
export function verifyPaymob(
  object: Record<string, unknown>,
  received: string,
  secret: string,
): boolean {
  if (!/^[a-fA-F0-9]{128}$/.test(received)) return false;
  try {
    return timingSafeEqual(
      createHmac("sha512", secret).update(paymobMessage(object)).digest(),
      Buffer.from(received, "hex"),
    );
  } catch {
    return false;
  }
}
export async function createPaymobCheckout(
  order: Order,
  email: string,
  phone: string,
  name: string,
  lang: string,
) {
  const secret = process.env.PAYMOB_SECRET_KEY,
    publicKey = process.env.PAYMOB_PUBLIC_KEY;
  const integrations = process.env.PAYMOB_INTEGRATION_IDS?.split(",")
    .map(Number)
    .filter((n) => Number.isInteger(n) && n > 0);
  if (
    !secret ||
    !publicKey ||
    !integrations?.length ||
    !process.env.PAYMOB_HMAC_SECRET
  )
    throw new DomainError("Paymob is not configured", 503);
  if (order.currency !== "EGP")
    throw new DomainError("This Paymob integration accepts EGP only");
  const result = await fetch("https://accept.paymob.com/v1/intention/", {
    method: "POST",
    headers: {
      Authorization: `Token ${secret}`,
      "Content-Type": "application/json",
    },
    signal: AbortSignal.timeout(15000),
    body: JSON.stringify({
      amount: order.amount,
      currency: order.currency,
      payment_methods: integrations,
      items: [
        {
          name: `Voxa ${order.tier} ${order.interval}`,
          amount: order.amount,
          quantity: 1,
        },
      ],
      billing_data: {
        first_name: name.split(" ")[0],
        last_name: name.split(" ").slice(1).join(" ") || "Caregiver",
        email,
        phone_number: phone,
        country: "EG",
      },
      special_reference: order.id,
      notification_url: `${appUrl()}/api/webhooks/paymob`,
      redirection_url: `${appUrl()}/${lang}/checkout/success?order=${order.id}`,
    }),
  });
  if (!result.ok)
    throw new DomainError(
      "Paymob could not start checkout. Please try again.",
      502,
    );
  const data = await result.json();
  const id = data.intention_order_id ?? data.order?.id;
  if (!data.client_secret || !id)
    throw new DomainError("Unexpected Paymob checkout response", 502);
  const url = new URL("https://accept.paymob.com/unifiedcheckout/");
  url.searchParams.set("publicKey", publicKey);
  url.searchParams.set("clientSecret", data.client_secret);
  return { url: url.toString(), id: String(id) };
}
