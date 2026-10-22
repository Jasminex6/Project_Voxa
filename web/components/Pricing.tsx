"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowUpRight, Check, ShieldCheck, WifiOff } from "lucide-react";
import {
  catalog,
  type Currency,
  type Interval,
  type PaidTier,
} from "@/lib/catalog";
import { api } from "@/lib/browser";
import type { Lang } from "./Shell";

export function Pricing({ lang }: { lang: Lang }) {
  const ar = lang === "ar";
  const [currency, setCurrency] = useState<Currency>("EGP"),
    [interval, setInterval] = useState<Interval>("monthly");
  const [config, setConfig] = useState<{
    demo: boolean;
    stripe: boolean;
    paymob: boolean;
    authConfigured: boolean;
  } | null>(null);
  const [gateway, setGateway] = useState("stripe"),
    [busy, setBusy] = useState(""),
    [error, setError] = useState("");
  const [name, setName] = useState(""),
    [phone, setPhone] = useState("");
  useEffect(() => {
    api<typeof config>("configuration")
      .then((c) => {
        setConfig(c);
        if (c?.demo) setGateway("demo");
        else if (!c?.stripe && c?.paymob) setGateway("paymob");
      })
      .catch(() =>
        setError(
          ar ? "تعذر تحميل خيارات الدفع" : "Could not load payment options",
        ),
      );
  }, [ar]);
  const checkoutReady = !!config && (config.demo || (config.authConfigured &&
    (gateway === "paymob" ? config.paymob && currency === "EGP" : config.stripe)));
  async function checkout(tier: PaidTier) {
    setBusy(tier);
    setError("");
    try {
      if (config?.demo) await api("auth/demo", {});
      const result = await api<{ url: string }>("checkout", {
        tier,
        currency,
        interval: tier === "CLINICAL_SLP" ? "monthly" : interval,
        gateway,
        lang,
        ...(gateway === "paymob" ? { name, phone } : {}),
      });
      window.location.assign(result.url);
    } catch (e) {
      setError((e as Error).message);
      setBusy("");
    }
  }
  const format = (n: number) =>
    new Intl.NumberFormat(ar ? "ar-EG" : "en", {
      style: "currency",
      currency,
      maximumFractionDigits: n % 100 === 0 ? 0 : 2,
    }).format(n / 100);
  const features = {
    FREE: ar
      ? [
          "ملف طفل واحد",
          "٣ كلمات أساسية",
          "تجربة مكتبة التمرين",
          "استقبال ملف عبر الأجهزة",
        ]
      : [
          "One child profile",
          "Three core words",
          "Practice library preview",
          "Receive a nearby handover",
        ],
    FAMILY_PRO: ar
      ? [
          "أصوات مخصصة بلا حدود",
          "ملفات أطفال متعددة",
          "مكتبة التمرين الكاملة",
          "إرسال واستقبال الملفات",
          "تفعيل على جهازين",
        ]
      : [
          "Unlimited custom sounds",
          "Multiple child profiles",
          "Full practice library",
          "Send & receive handovers",
          "Activate on two devices",
        ],
    CLINICAL_SLP: ar
      ? [
          "كل مزايا Family Pro",
          "تفعيل حتى ٢٥ جهازاً",
          "متابعة الممارسة محلياً",
          "إدارة التراخيص والأجهزة",
          "مصمم لفرق الرعاية",
        ]
      : [
          "Everything in Family Pro",
          "Up to 25 activated devices",
          "Local practice history",
          "License & device management",
          "Designed for care teams",
        ],
  };
  return (
    <section className="section pricing-page">
      <div className="page-intro">
        <div className="eyebrow">
          {ar ? "خطط تنمو معك" : "ROOM TO GROW, AT YOUR PACE"}
        </div>
        <h1>
          {ar ? (
            <>
              تواصل أكثر.
              <br />
              <em>تعقيد أقل.</em>
            </>
          ) : (
            <>
              More connection.
              <br />
              <em>Less complication.</em>
            </>
          )}
        </h1>
        <p>
          {ar
            ? "أدوات الطوارئ والترجمات الحالية تبقى متاحة في كل خطة."
            : "Emergency tools and existing translations stay available on every plan."}
        </p>
      </div>
      <div className="pricing-controls">
        <div className="segmented" aria-label="Billing period">
          {(["monthly", "annual"] as Interval[]).map((i) => (
            <button
              key={i}
              onClick={() => setInterval(i)}
              aria-pressed={interval === i}
              className={interval === i ? "selected" : ""}
            >
              {i === "monthly"
                ? ar
                  ? "شهري"
                  : "Monthly"
                : ar
                  ? "سنوي · قيمة أفضل"
                  : "Annual · better value"}
            </button>
          ))}
        </div>
        <label className="currency">
          {ar ? "العملة" : "Currency"}
          <select
            value={currency}
            onChange={(e) => {
              setCurrency(e.target.value as Currency);
              if (e.target.value === "SAR" && gateway === "paymob")
                setGateway("stripe");
            }}
          >
            <option>EGP</option>
            <option>SAR</option>
          </select>
        </label>
      </div>
      {config?.demo && (
        <div className="notice">
          <SparkleDot />
          {ar
            ? "عرض تجريبي: لا يتم تحصيل أي مبلغ."
            : "Demo checkout: no money is charged."}
        </div>
      )}
      <div className="pricing-grid">
        {(["FREE", "FAMILY_PRO", "CLINICAL_SLP"] as const).map((tier) => (
          <article
            key={tier}
            className={`price-card ${tier === "FAMILY_PRO" ? "featured" : ""}`}
          >
            {tier === "FAMILY_PRO" && (
              <div className="recommended">
                {ar ? "للممارسة اليومية" : "FOR EVERYDAY CONNECTION"}
              </div>
            )}
            <div className="tier-label">
              {tier === "FREE"
                ? ar
                  ? "البداية"
                  : "A first connection"
                : tier === "FAMILY_PRO"
                  ? ar
                    ? "مساحة للعائلة"
                    : "A little more room"
                  : ar
                    ? "لفرق الرعاية"
                    : "For care teams"}
            </div>
            <h2>
              {tier === "FREE" ? (ar ? "مجاني" : "Free") : catalog[tier].name}
            </h2>
            <div className="price">
              {tier === "FREE"
                ? format(0)
                : format(catalog[tier].prices[currency][tier === "CLINICAL_SLP" ? "monthly" : interval]!)}
              <small>
                {tier === "FREE"
                  ? ar
                    ? "دائماً"
                    : "always"
                  : tier !== "CLINICAL_SLP" && interval === "annual"
                    ? ar
                      ? "/ سنة"
                      : "/ year"
                    : ar
                      ? "/ شهر"
                      : "/ month"}
              </small>
            </div>
            <p>
              {tier === "FREE"
                ? ar
                  ? "ابدأ بالأصوات الأساسية."
                  : "Start with the essentials."
                : tier === "FAMILY_PRO"
                  ? ar
                    ? "للممارسة والتواصل كل يوم."
                    : "For practice and connection, every day."
                  : ar
                    ? "لتنظيم الوصول عبر فريقك."
                    : "Organize access across your team."}
            </p>
            {tier === "FREE" ? (
              <Link className="button quiet full" href={`/${lang}/dashboard`}>
                {ar ? "ابدأ مجاناً" : "Start with Free"}
                <ArrowUpRight size={15} />
              </Link>
            ) : (
              <button
                className={`button full ${tier === "FAMILY_PRO" ? "primary" : "quiet"}`}
                disabled={!!busy || !checkoutReady}
                onClick={() => checkout(tier)}
              >
                {busy === tier
                  ? ar
                    ? "جارٍ التحضير…"
                    : "Preparing…"
                  : config && !checkoutReady
                    ? ar ? "الدفع غير متاح حالياً" : "Checkout unavailable"
                  : config?.demo
                    ? ar
                      ? "جرّب الترقية"
                      : "Try the upgrade"
                    : ar
                      ? "اختر الخطة"
                      : "Choose this plan"}
                <ArrowUpRight size={15} />
              </button>
            )}
            <ul>
              {features[tier].map((f) => (
                <li key={f}>
                  <Check size={16} />
                  {f}
                </li>
              ))}
            </ul>
          </article>
        ))}
      </div>
      {!config?.demo && (
        <div className="payment-options">
          <label>
            {ar ? "طريقة الدفع" : "Payment provider"}
            <select
              value={gateway}
              onChange={(e) => setGateway(e.target.value)}
            >
              <option value="stripe" disabled={!config?.stripe}>
                Stripe · {ar ? "بطاقات" : "Cards"}
              </option>
              <option
                value="paymob"
                disabled={!config?.paymob || currency !== "EGP"}
              >
                Paymob · EGP
              </option>
            </select>
          </label>
          {gateway === "paymob" && (
            <>
              <label>
                {ar ? "الاسم للفوترة" : "Billing name"}
                <input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  autoComplete="name"
                />
              </label>
              <label>
                {ar ? "رقم الهاتف" : "Phone number"}
                <input
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  type="tel"
                  autoComplete="tel"
                />
              </label>
            </>
          )}
          <Link href={`/${lang}/signin`}>
            {ar ? "سجّل الدخول قبل الشراء" : "Sign in before purchasing"}
          </Link>
        </div>
      )}
      {error && (
        <div className="error" role="alert">
          {error}{" "}
          <Link href={`/${lang}/signin`}>
            {ar ? "تسجيل الدخول" : "Sign in"}
          </Link>
        </div>
      )}
      <div className="plan-guarantees">
        <span>
          <ShieldCheck />
          {ar
            ? "لا حذف لبيانات الطفل عند الانتهاء"
            : "No child data deleted on expiry"}
        </span>
        <span>
          <WifiOff />
          {ar ? "وصول مدفوع يعمل دون إنترنت" : "Paid access works offline"}
        </span>
      </div>
      <div className="faq">
        <h2>{ar ? "أسئلة قبل البداية" : "A few things you might wonder."}</h2>
        {[
          [
            ar ? "هل أحتاج إلى الإنترنت؟" : "Do I need internet?",
            ar
              ? "الترجمة الأساسية تعمل محلياً. تحتاج إلى اتصال عند الشراء والتفعيل والتجديد. مكتبة اللغة المحلية للتمرين يجب أن تكون متاحة على الجهاز."
              : "Core translation runs locally. Purchasing, first activation, and renewing need a connection. Speech practice requires an available on-device language service.",
          ],
          [
            ar
              ? "ماذا يحدث عند انتهاء الاشتراك؟"
              : "What happens when a subscription expires?",
            ar
              ? "يستمر الوصول المدفوع حتى تاريخ الانتهاء الموثق، ثم سبعة أيام سماح. لا تُحذف الملفات أو الأصوات، وتبقى الطوارئ والترجمات الحالية متاحة."
              : "Paid access lasts through the verified period, followed by seven days of grace. Profiles and trained sounds are preserved. Emergency tools and existing translations remain available.",
          ],
          [
            ar
              ? "هل الدفع المصري يتجدد تلقائياً؟"
              : "Do Egyptian payments renew automatically?",
            ar
              ? "في هذا الإصدار، خطط Paymob مدفوعة مسبقاً لفترة محددة وتحتاج إلى تجديد يدوي. طرق الدفع المتاحة تعتمد على حساب التاجر."
              : "In this release, Paymob plans are prepaid for a fixed period and renewed manually. Available payment methods depend on the merchant’s configured integrations.",
          ],
          [
            ar
              ? "هل يحل فوكسـا محل المعالج؟"
              : "Does Voxa replace a therapist?",
            ar
              ? "لا. فوكسـا أداة تواصل وممارسة. درجات المطابقة ليست تشخيصاً أو مقياساً طبياً مثبتاً."
              : "No. Voxa is a communication and practice tool. Matching scores are not a diagnosis or a validated clinical measurement.",
          ],
        ].map(([q, a]) => (
          <details key={q}>
            <summary>
              {q}
              <span>+</span>
            </summary>
            <p>{a}</p>
          </details>
        ))}
      </div>
    </section>
  );
}
function SparkleDot() {
  return <span className="live-dot" />;
}
