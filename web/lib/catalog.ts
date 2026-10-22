export const tiers = ["FREE", "FAMILY_PRO", "CLINICAL_SLP"] as const;
export type Tier = (typeof tiers)[number];
export type PaidTier = Exclude<Tier, "FREE">;
export type Currency = "EGP" | "USD";
export type Interval = "monthly" | "annual";
// The execution plan's demo prices. Business-plan prices can be changed here.
// Minor currency units; the server always derives the amount from this catalog.
export const catalog = {
  FAMILY_PRO: {
    name: "Family Pro",
    devices: 2,
    prices: {
      EGP: { monthly: 25000, annual: 199900 },
      USD: { monthly: 999, annual: 7900 },
    },
  },
  CLINICAL_SLP: {
    name: "Clinical SLP",
    devices: 25,
    prices: {
      EGP: { monthly: 299900, annual: 2999900 },
      USD: { monthly: 4900, annual: 49000 },
    },
  },
} as const;
export function price(
  tier: PaidTier,
  currency: Currency,
  interval: Interval,
): number {
  return catalog[tier].prices[currency][interval];
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
