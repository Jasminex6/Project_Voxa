"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import QRCode from "qrcode";
import {
  ArrowRight,
  CheckCircle2,
  Copy,
  KeyRound,
  LoaderCircle,
  LogOut,
  QrCode,
  ShieldCheck,
  Smartphone,
  WifiOff,
} from "lucide-react";
import { api, authClient } from "@/lib/browser";
import type { Activation, License, Order, Subscription } from "@/lib/store";
import type { Lang } from "./Shell";
import { Reports } from "./Reports";
type DashboardData = {
  user: { id: string; email: string };
  demo: boolean;
  orders: Order[];
  licenses: License[];
  subscriptions: Subscription[];
  devices: Activation[];
};
const date = (n: number, lang: Lang) =>
  new Intl.DateTimeFormat(lang === "ar" ? "ar-EG" : "en", {
    dateStyle: "medium",
  }).format(n);

export function SignIn({ lang }: { lang: Lang }) {
  const ar = lang === "ar",
    [email, setEmail] = useState(""),
    [message, setMessage] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [demo, setDemo] = useState(false);
  useEffect(() => {
    api<{ demo: boolean }>("configuration")
      .then((c) => setDemo(c.demo))
      .catch(() => {});
  }, []);
  async function signin(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const client = authClient();
      if (!client)
        throw new Error(
          ar
            ? "تسجيل الدخول المباشر غير مهيأ بعد."
            : "Live sign-in is not configured yet.",
        );
      const result = await client.auth.signInWithOtp({
        email,
        options: {
          emailRedirectTo: `${window.location.origin}/${lang}/dashboard`,
        },
      });
      if (result.error) throw result.error;
      setMessage(
        ar
          ? "تحقق من بريدك للحصول على رابط الدخول."
          : "Check your inbox for your sign-in link.",
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="section narrow auth-page">
      <span className="page-icon">
        <KeyRound />
      </span>
      <div className="eyebrow">
        {ar ? "مساحتك العائلية" : "YOUR FAMILY SPACE"}
      </div>
      <h1>{ar ? "أهلاً بعودتك." : "Welcome back."}</h1>
      <p>
        {ar
          ? "أدر اشتراكك وفعّل أجهزتك. لا تُرفع تسجيلات طفلك هنا."
          : "Manage your plan and activate your devices. Your child’s recordings aren’t uploaded here."}
      </p>
      <form onSubmit={signin} className="form">
        <label>
          {ar ? "البريد الإلكتروني" : "Email address"}
          <input
            required
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
          />
        </label>
        <button disabled={busy} className="button primary">
          {busy ? <LoaderCircle className="spin" /> : null}
          {ar ? "أرسل رابط الدخول" : "Send a sign-in link"}
          <ArrowRight size={16} />
        </button>
      </form>
      {demo && (
        <button
          className="button quiet full"
          onClick={async () => {
            await api("auth/demo", {});
            window.location.assign(`/${lang}/dashboard`);
          }}
        >
          {ar
            ? "ادخل العرض المحلي دون بريد"
            : "Enter local demo · no email required"}
        </button>
      )}
      {message && (
        <p role="status" className="notice">
          {message}
        </p>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}

export function Dashboard({ lang }: { lang: Lang }) {
  const ar = lang === "ar",
    [data, setData] = useState<DashboardData | null>(null),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(""),
    [exchange, setExchange] = useState<{
      code: string;
      expiresAt: number;
      licenseId: string;
    } | null>(null);
  const refresh = () =>
    api<DashboardData>("dashboard")
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  useEffect(() => {
    let active = true;
    const run = async () => {
      await authClient()?.auth.getSession();
      if (active) await refresh();
    };
    void run();
    return () => {
      active = false;
    };
  }, []);
  async function activate(licenseId: string) {
    setBusy(licenseId);
    setError("");
    try {
      const result = await api<{ code: string; expiresAt: number }>(
        "license/exchange",
        { licenseId },
      );
      setExchange({ ...result, licenseId });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy("");
    }
  }
  return (
    <section className="section account-page">
      <div className="account-heading">
        <div>
          <div className="eyebrow">
            {ar ? "كل شيء في مكانه" : "A LITTLE SPACE FOR YOUR PLAN"}
          </div>
          <h1>{ar ? "مساحتك في فوكسـا." : "Your Voxa space."}</h1>
          <p>
            {data?.user.email ??
              (ar
                ? "التراخيص والأجهزة والاشتراكات"
                : "Licenses, devices, and subscriptions")}
          </p>
        </div>
        {data && (
          <button
            className="button quiet small"
            onClick={async () => {
              await authClient()?.auth.signOut();
              await api("auth/logout", {});
              window.location.assign(`/${lang}/signin`);
            }}
          >
            <LogOut size={15} />
            {ar ? "خروج" : "Sign out"}
          </button>
        )}
      </div>
      {loading && (
        <p className="loading">
          <LoaderCircle className="spin" />
          {ar ? "جارٍ التحميل…" : "Loading your space…"}
        </p>
      )}
      {error && (
        <div className="error" role="alert">
          {error}{" "}
          <Link href={`/${lang}/signin`}>
            {ar ? "تسجيل الدخول" : "Sign in"}
          </Link>
        </div>
      )}
      {data?.demo && (
        <div className="notice">
          {ar
            ? "حساب عرض محلي. لا تُحصّل أموال، ولا تظهر بيانات أطفال حقيقية."
            : "Local demo account. No money is charged, and no real child records are displayed."}
        </div>
      )}
      {data && (
        <>
          <div className="account-stats">
            <div>
              <KeyRound />
              <b>{data.licenses.length}</b>
              <span>{ar ? "تراخيص" : "Licenses"}</span>
            </div>
            <div>
              <Smartphone />
              <b>{data.devices.length}</b>
              <span>{ar ? "أجهزة مفعّلة" : "Activated devices"}</span>
            </div>
            <div>
              <ShieldCheck />
              <b>{ar ? "محلي" : "Local"}</b>
              <span>{ar ? "معالجة الصوت" : "Voice processing"}</span>
            </div>
          </div>
          {!data.licenses.length ? (
            <div className="empty-state">
              <KeyRound size={32} />
              <h2>
                {ar
                  ? "ابدأ مجاناً، وترقَّ حين تحتاج."
                  : "Start free. Grow when you’re ready."}
              </h2>
              <p>
                {ar
                  ? "يمكنك استخدام التطبيق دون ترخيص مدفوع."
                  : "You can use the Android app without a paid license."}
              </p>
              <Link className="button primary" href={`/${lang}/pricing`}>
                {ar ? "استكشف الخطط" : "Explore plans"}
                <ArrowRight size={16} />
              </Link>
            </div>
          ) : (
            <div className="license-list">
              {data.licenses.map((license) => {
                const sub = data.subscriptions.find(
                    (s) => s.id === license.subscriptionId,
                  ),
                  devices = data.devices.filter(
                    (d) => d.licenseId === license.id,
                  );
                return (
                  <article className="license-card" key={license.id}>
                    <div className="license-card-header">
                      <div>
                        <span className="eyebrow">
                          {license.tier === "FAMILY_PRO"
                            ? "FAMILY PRO"
                            : "CLINICAL SLP"}
                        </span>
                        <h2>
                          {ar
                            ? "مساحة أكبر للتواصل"
                            : "More room for connection"}
                        </h2>
                      </div>
                      <span
                        className={`badge ${license.isRevoked ? "danger" : ""}`}
                      >
                        {license.isRevoked
                          ? ar
                            ? "ملغى"
                            : "Revoked"
                          : sub && sub.currentPeriodEnd < Date.now()
                            ? ar
                              ? "انتهى"
                              : "Expired"
                            : sub?.status === "canceled"
                              ? ar
                                ? "ينتهي بنهاية الفترة"
                                : "Cancels at period end"
                              : ar
                                ? "نشط"
                                : "Active"}
                      </span>
                    </div>
                    <div className="license-reference">
                      <small>
                        {ar
                          ? "مرجع الترخيص (ليس رمز التفعيل)"
                          : "LICENSE REFERENCE · NOT AN ACTIVATION CODE"}
                      </small>
                      <code dir="ltr">{license.licenseKey}</code>
                    </div>
                    <div className="license-facts">
                      <span>
                        {ar ? "مدفوع حتى" : "Paid through"}
                        <b>{sub ? date(sub.currentPeriodEnd, lang) : "—"}</b>
                      </span>
                      <span>
                        {ar ? "الأجهزة" : "Devices"}
                        <b dir="ltr">
                          {devices.length} / {license.maxDevices}
                        </b>
                      </span>
                      <span>
                        {ar ? "التجديد" : "Renewal"}
                        <b>
                          {sub?.gateway === "stripe"
                            ? ar
                              ? "يُدار عبر Stripe"
                              : "Managed in Stripe"
                            : ar
                              ? "يدوي / مسبق الدفع"
                              : "Manual / prepaid"}
                        </b>
                      </span>
                    </div>
                    <div className="hero-actions">
                      <button
                        className="button primary"
                        disabled={
                          !!busy ||
                          license.isRevoked ||
                          !sub ||
                          sub.currentPeriodEnd < Date.now()
                        }
                        onClick={() => activate(license.id)}
                      >
                        <QrCode size={16} />
                        {busy === license.id
                          ? ar
                            ? "جارٍ التحضير…"
                            : "Preparing…"
                          : ar
                            ? "تفعيل أو تجديد جهاز"
                            : "Activate or renew a device"}
                      </button>
                      {sub?.gateway === "stripe" && (
                        <button
                          className="button quiet"
                          onClick={async () => {
                            try {
                              const r = await api<{ url: string }>(
                                "billing/portal",
                                { subscriptionId: sub.id },
                              );
                              window.location.assign(r.url);
                            } catch (e) {
                              setError((e as Error).message);
                            }
                          }}
                        >
                          {ar ? "إدارة الاشتراك" : "Manage subscription"}
                        </button>
                      )}
                      {data.demo && sub?.status === "active" && (
                        <button
                          className="text-link"
                          onClick={async () => {
                            await api("demo/cancel", {
                              subscriptionId: sub.id,
                            });
                            await refresh();
                          }}
                        >
                          {ar ? "محاكاة الإلغاء" : "Simulate cancellation"}
                        </button>
                      )}
                    </div>
                    {devices.length > 0 && (
                      <details className="devices">
                        <summary>
                          {ar
                            ? "عرض الأجهزة المقترنة"
                            : "View paired installations"}
                        </summary>
                        {devices.map((d, i) => (
                          <div key={d.id}>
                            <Smartphone size={15} />
                            <span>
                              {ar ? "جهاز" : "Device"} {i + 1}
                            </span>
                            <code dir="ltr">{d.deviceId.slice(0, 8)}…</code>
                            <small>{date(d.lastPingAt, lang)}</small>
                          </div>
                        ))}
                      </details>
                    )}
                  </article>
                );
              })}
            </div>
          )}
          <div className="offline-note">
            <WifiOff />
            <p>
              {ar
                ? "بعد التفعيل، يتحقق التطبيق من الترخيص محلياً حتى نهاية الفترة وفترة السماح. الإلغاء لا يحذف بيانات الطفل."
                : "After activation, the app verifies your signed license locally through its paid period and grace period. Cancellation never deletes child data."}
            </p>
          </div>
          <details className="order-history">
            <summary>{ar ? "سجل الطلبات" : "Order history"}</summary>
            {data.orders.map((order) => (
              <Link
                key={order.id}
                href={`/${lang}/checkout/success?order=${order.id}`}
              >
                <span>{order.tier.replaceAll("_", " ")}</span>
                <span>{order.status}</span>
                <span>{date(order.createdAt, lang)}</span>
              </Link>
            ))}
          </details>
          <Reports lang={lang} />
        </>
      )}
      {exchange && (
        <ActivationBox
          lang={lang}
          code={exchange.code}
          expiresAt={exchange.expiresAt}
          onClose={() => setExchange(null)}
        />
      )}
    </section>
  );
}

export function ActivationBox({
  lang,
  code,
  expiresAt,
  onClose,
}: {
  lang: Lang;
  code: string;
  expiresAt: number;
  onClose?: () => void;
}) {
  const ar = lang === "ar",
    [qr, setQr] = useState(""),
    [copied, setCopied] = useState(false),
    [now, setNow] = useState(Date.now());
  const link = `voxa://activate?code=${code}`;
  useEffect(() => {
    let active = true;
    void QRCode.toDataURL(link, {
      width: 240,
      margin: 2,
      errorCorrectionLevel: "M",
    }).then((value) => {
      if (active) setQr(value);
    });
    return () => {
      active = false;
    };
  }, [link]);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const expired = now >= expiresAt;
  return (
    <div className="activation-box">
      <div className="activation-copy">
        <span className="eyebrow">
          {ar ? "الخطوة التالية: جهازك" : "NEXT STOP: YOUR DEVICE"}
        </span>
        <h2>
          {ar ? "فعّل. ثم انطلق دون إنترنت." : "Activate. Then go offline."}
        </h2>
        <p>
          {ar
            ? "افتح الرابط على هاتف Android، أو امسح الرمز من فوكسـا ← الملف ← الاشتراك والترخيص."
            : "Open the link on Android, or paste your code in Voxa → Profile → My plan."}
        </p>
        {expired ? (
          <p className="error">
            {ar
              ? "انتهى الرمز. أنشئ رمزاً جديداً من حسابك."
              : "This code expired. Create another from your account."}
          </p>
        ) : (
          <a className="button primary" href={link}>
            <Smartphone size={17} />
            {ar ? "افتح فوكسـا وفعّل" : "Open Voxa & activate"}
          </a>
        )}
        <label>
          {ar ? "رمز الاستبدال المؤقت" : "Short-lived exchange code"}
          <div className="copy-field">
            <input
              readOnly
              value={code}
              dir="ltr"
              aria-label="Activation exchange code"
            />
            <button
              className="icon-button"
              aria-label={ar ? "نسخ الرمز" : "Copy code"}
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(code);
                  setCopied(true);
                } catch {
                  setCopied(false);
                }
              }}
            >
              {copied ? <CheckCircle2 size={18} /> : <Copy size={18} />}
            </button>
          </div>
        </label>
        <small>
          {ar
            ? "يقترن بتثبيت واحد. يحتاج إلى الإنترنت مرة واحدة. صالح حتى"
            : "Pairs to one installation. Needs internet once. Expires at"}{" "}
          {new Date(expiresAt).toLocaleTimeString(ar ? "ar-EG" : "en")}
        </small>
        {onClose && (
          <button className="text-link" onClick={onClose}>
            {ar ? "إغلاق" : "Close"}
          </button>
        )}
      </div>
      <div className="qr-wrap">
        {qr && (
          <img
            src={qr}
            alt={ar ? "رمز تفعيل فوكسـا" : "Voxa activation QR code"}
            width="240"
            height="240"
          />
        )}
        <span>{ar ? "امسح من تطبيق فوكسـا" : "Scan with Voxa on Android"}</span>
      </div>
    </div>
  );
}

export function Checkout({
  lang,
  demo = false,
}: {
  lang: Lang;
  demo?: boolean;
}) {
  const ar = lang === "ar",
    params = useSearchParams(),
    id = params.get("order"),
    [data, setData] = useState<{
      order: Order;
      license?: License;
      subscription?: Subscription;
    } | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [exchange, setExchange] = useState<{
      code: string;
      expiresAt: number;
    } | null>(null),
    [ticks, setTicks] = useState(0);
  useEffect(() => {
    if (!id) {
      setError(ar ? "معرف الطلب مفقود" : "Missing order reference");
      return;
    }
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    const load = async () => {
      try {
        const result = await api<typeof data>(`orders/${id}`);
        if (!active) return;
        setData(result);
        if (result?.order.status === "pending" && !demo && ticks < 30)
          timer = setTimeout(() => setTicks((t) => t + 1), 2000);
      } catch (e) {
        if (active) setError((e as Error).message);
      }
    };
    void load();
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [id, demo, ticks, ar]);
  async function complete() {
    if (!id) return;
    setBusy(true);
    try {
      await api("checkout/demo/complete", { orderId: id });
      window.location.assign(`/${lang}/checkout/success?order=${id}`);
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }
  async function getCode() {
    if (!data?.license) return;
    setBusy(true);
    try {
      setExchange(
        await api("license/exchange", { licenseId: data.license.id }),
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="section account-page">
      <div className="page-intro">
        <span className="page-icon">
          {data?.order.status === "paid" ? <CheckCircle2 /> : <ShieldCheck />}
        </span>
        <div className="eyebrow">
          {demo
            ? ar
              ? "عرض محلي آمن"
              : "SAFE LOCAL SIMULATION"
            : ar
              ? "طلبك في فوكسـا"
              : "YOUR VOXA ORDER"}
        </div>
        <h1>
          {demo
            ? ar
              ? "جرّب تجربة الدفع."
              : "Try the checkout journey."
            : data?.license
              ? ar
                ? "مساحة أكبر، جاهزة لك."
                : "A little more room, ready for you."
              : ar
                ? "نتحقق من طلبك."
                : "We’re checking your order."}
        </h1>
      </div>
      {error && (
        <p role="alert" className="error">
          {error}{" "}
          <Link href={`/${lang}/signin`}>
            {ar ? "تسجيل الدخول" : "Sign in"}
          </Link>
        </p>
      )}
      {data && (
        <div className="checkout-summary">
          <div>
            <b>{data.order.tier.replaceAll("_", " ")}</b>
            <span>
              {data.order.interval} ·{" "}
              {new Intl.NumberFormat(ar ? "ar-EG" : "en", {
                style: "currency",
                currency: data.order.currency,
              }).format(data.order.amount / 100)}
            </span>
          </div>
          <span className="badge">
            {demo ? "DEMO" : data.order.status.toUpperCase()}
          </span>
        </div>
      )}
      {demo && data && (
        <div className="demo-checkout">
          <ShieldCheck size={36} />
          <p>
            {ar
              ? "لا تدخل أي بيانات بطاقة. هذا الزر يحاكي تأكيد الدفع على خادم العرض المحلي."
              : "Do not enter any card details. This button simulates verified payment on the local demo server."}
          </p>
          <button disabled={busy} className="button primary" onClick={complete}>
            {busy ? (
              <LoaderCircle className="spin" />
            ) : (
              <CheckCircle2 size={17} />
            )}{" "}
            {ar
              ? "محاكاة دفع ناجح — دون تحصيل"
              : "Simulate successful payment · no charge"}
          </button>
          <Link className="text-link" href={`/${lang}/pricing`}>
            {ar ? "رجوع إلى الخطط" : "Back to plans"}
          </Link>
        </div>
      )}
      {!demo && data?.license && !exchange && (
        <div className="empty-state">
          <p>
            {ar
              ? "تم تأكيد الدفع على الخادم. أنشئ رابط تفعيل قصير الصلاحية لجهازك."
              : "Payment has been verified by the server. Create a short-lived activation link for your device."}
          </p>
          <button disabled={busy} className="button primary" onClick={getCode}>
            {ar ? "إنشاء رمز تفعيل" : "Create activation code"}
            <QrCode size={16} />
          </button>
        </div>
      )}
      {!demo && data?.order.status === "pending" && (
        <div className="notice">
          <LoaderCircle className="spin" size={18} />
          {ar
            ? "ننتظر تأكيد مزود الدفع. العودة لهذه الصفحة لا تمنح وصولاً مدفوعاً بمفردها."
            : "Waiting for the payment provider’s verified confirmation. Returning to this page alone never grants paid access."}
          {ticks >= 30 && (
            <button className="text-link" onClick={() => setTicks(0)}>
              {ar ? "تحقق مجدداً" : "Check again"}
            </button>
          )}
        </div>
      )}
      {exchange && <ActivationBox lang={lang} {...exchange} />}
      <Link className="text-link" href={`/${lang}/dashboard`}>
        {ar ? "إلى حسابي" : "Go to my account"}
        <ArrowRight size={16} />
      </Link>
    </section>
  );
}
