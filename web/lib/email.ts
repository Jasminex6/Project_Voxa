import { randomUUID } from "node:crypto";
import { appUrl } from "./http";
import { demoMode, transaction } from "./store";

/** No delivery occurs in demo mode. Only the authenticated checkout email is used.
 * Bearer tokens and activation exchanges are never emailed; the account creates fresh codes.
 */
export async function notifyPaidOrder(orderId: string): Promise<void> {
  if (demoMode() || !process.env.RESEND_API_KEY) return;
  if (!process.env.VOXA_FROM_EMAIL)
    throw new Error("Fulfillment email sender is not configured");
  const order = await transaction(async (store) => {
    const sent = (await store.all("webhook_events")).some(
      (e) => e.gateway === "email" && e.gatewayEventId === orderId,
    );
    return sent
      ? null
      : (await store.all("orders")).find(
          (o) => o.id === orderId && o.status === "paid",
        );
  });
  if (!order?.billingEmail) return;
  const result = await fetch("https://api.resend.com/emails", {
    method: "POST",
    signal: AbortSignal.timeout(15000),
    headers: {
      Authorization: `Bearer ${process.env.RESEND_API_KEY}`,
      "Content-Type": "application/json",
      "Idempotency-Key": `voxa-fulfillment/${order.id}`,
    },
    body: JSON.stringify({
      from: process.env.VOXA_FROM_EMAIL,
      to: [order.billingEmail],
      subject: "Your Voxa plan is ready | اشتراك فوكسـا جاهز",
      text: `Your Voxa ${order.tier.replaceAll("_", " ")} payment is confirmed.\nSign in to create an activation QR or link for your Android installation:\n${appUrl()}/en/dashboard\n\nتم تأكيد الدفع. سجّل الدخول لإنشاء رمز تفعيل لجهازك:\n${appUrl()}/ar/dashboard\n\nOrder reference: ${order.id}\nYour child's voice recordings are not included in this email.`,
    }),
  });
  if (!result.ok) throw new Error("Fulfillment email delivery failed");
  await transaction(async (store) => {
    if (
      !(await store.all("webhook_events")).some(
        (e) => e.gateway === "email" && e.gatewayEventId === order.id,
      )
    )
      await store.put("webhook_events", {
        id: randomUUID(),
        userId: order.userId,
        gateway: "email",
        gatewayEventId: order.id,
        processedAt: Date.now(),
      });
  });
}
