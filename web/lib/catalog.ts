export const tiers = ["FREE", "FAMILY_PRO", "CLINICAL_SLP"] as const;
export type Tier = (typeof tiers)[number];
export type PaidTier = Exclude<Tier, "FREE">;
export type Currency = "EGP" | "SAR";
export type Interval = "monthly" | "annual";
// Business plan v2, section 07. Clinic subscriptions are monthly only.
// Minor currency units; the server always derives the amount from this catalog.
export const catalog = {
  FAMILY_PRO: {
    name: "Family Pro",
    devices: 2,
    prices: {
      EGP: { monthly: 34900, annual: 279900 },
      SAR: { monthly: 9900, annual: 89900 },
    },
  },
  CLINICAL_SLP: {
    name: "Clinical SLP",
    devices: 25,
    prices: {
      EGP: { monthly: 299900, annual: null },
      SAR: { monthly: 179900, annual: null },
    },
  },
} as const;
export function price(
  tier: PaidTier,
  currency: Currency,
  interval: Interval,
): number {
  const amount = catalog[tier].prices[currency][interval];
  if (amount === null) throw new Error("This plan is billed monthly only");
  return amount;
}
export function addPeriod(start: number, interval: Interval): number {
  const date = new Date(start);
  const day = date.getUTCDate();
  date.setUTCDate(1);
  if (interval === "annual") date.setUTCFullYear(date.getUTCFullYear() + 1);
  else date.setUTCMonth(date.getUTCMonth() + 1);
  const last = new Date(
    Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 0),
  ).getUTCDate();
  date.setUTCDate(Math.min(day, last));
  return date.getTime();
}
