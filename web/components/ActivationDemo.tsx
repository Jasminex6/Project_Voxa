"use client";
import Link from "next/link";
import { useState } from "react";
import { useSearchParams } from "next/navigation";
import QRCode from "qrcode";
import { Check, Copy, KeyRound, QrCode, ShieldCheck, Smartphone } from "lucide-react";
import { api } from "@/lib/browser";
import type { PaidTier } from "@/lib/catalog";
import type { Lang } from "./Shell";

export function ActivationDemo({ lang }: { lang: Lang }) {
  const ar = lang === "ar";
  const params = useSearchParams();
  const [tier, setTier] = useState<PaidTier>(params.get("tier") === "CLINICAL_SLP" ? "CLINICAL_SLP" : "FAMILY_PRO");
  const [deviceId, setDeviceId] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);
  const [result, setResult] = useState<{ token: string; deepLink: string; tier: PaidTier; expiresAt: number; qr: string } | null>(null);
  async function generate(event: React.FormEvent) {
    event.preventDefault(); setError(""); setBusy(true); setResult(null); setCopied(false);
    try {
      const response = await api<{ token: string; deepLink: string; tier: PaidTier; expiresAt: number }>("demo/activate", { tier, deviceId: deviceId.trim().toLowerCase() });
      const qr = await QRCode.toDataURL(response.deepLink, { width: 560, margin: 3, errorCorrectionLevel: "M", color: { dark: "#09111f", light: "#ffffff" } });
      setResult({ ...response, qr });
    } catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }
  return <section className="section activation-page">
    <div className="page-intro">
      <div className="eyebrow"><KeyRound size={16} /> {ar ? "من الموقع إلى تطبيقك" : "FROM THE WEBSITE TO YOUR APP"}</div>
      <h1>{ar ? "اختر خطتك. فعّلها." : <>Your plan.<br /><em>Ready on your phone.</em></>}</h1>
      <p>{ar ? "جرّب مزايا الخطة في تطبيق فوكسا برمز تفعيل حقيقي، دون بطاقة أو دفع." : "Try a Voxa plan with a real app activation code. No card, no charge, no account needed."}</p>
    </div>
    <div className="activation-layout">
      <div className="activation-steps">
        {[
          [ar ? "افتح خطتي في التطبيق" : "Open My plan in Voxa", ar ? "من القائمة الجانبية ← الملف الشخصي ← تفعيل رمز. انسخ معرّف التثبيت." : "Sidebar → Profile → Activate a code. Copy your installation ID."],
          [ar ? "أنشئ رمز خطتك" : "Generate your plan code", ar ? "الصق المعرّف واختر Family Pro أو Clinical SLP. الرمز مخصص لهذا التثبيت فقط." : "Paste the ID and choose Family Pro or Clinical SLP. Your code works only on that installation."],
          [ar ? "امسح الرمز أو الصقه" : "Scan or paste, then confirm", ar ? "امسح QR من التطبيق أو الصق رمز التفعيل، ثم أدخل رمز ولي الأمر. تعمل المزايا دون اتصال." : "Scan the QR inside Voxa or paste the activation code, then enter your caregiver PIN. Plan verification works offline."],
        ].map(([title, body], i) => <article key={i}><span className="step-number">0{i + 1}</span><div><h2>{title}</h2><p>{body}</p></div></article>)}
        <div className="notice"><ShieldCheck size={18} /> {ar ? "عرض هاكاثون: ٧ أيام على جهاز واحد، مع فترة السماح دون اتصال. ليس اشتراكاً مدفوعاً. نستخدم معرّف تثبيت عشوائياً فقط؛ لا نطلب بيانات الطفل أو صوته." : "Hackathon demo: seven days on one installation, plus offline grace. This is not a paid subscription. Pairing uses a random installation ID, never child data or audio."}</div>
      </div>
      <div className="activation-panel">
        <form className="form" onSubmit={generate}>
          <div className="panel-heading"><span className="page-icon"><Smartphone /></span><div><h2>{ar ? "ربط وتفعيل" : "Pair & activate"}</h2><p>{ar ? "يتطلب إصدار العرض المحدّث من تطبيق Android." : "Use the updated Voxa Android demo build."}</p></div></div>
          <label>{ar ? "الخطة" : "Plan"}<select value={tier} onChange={e => { setTier(e.target.value as PaidTier); setResult(null); }}><option value="FAMILY_PRO">Family Pro</option><option value="CLINICAL_SLP">Clinical SLP</option></select></label>
          <label>{ar ? "معرّف التثبيت من التطبيق" : "Installation ID from My plan"}<input required autoComplete="off" spellCheck={false} dir="ltr" value={deviceId} onChange={e => { setDeviceId(e.target.value); setResult(null); }} placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" pattern="[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}" /></label>
          <button className="button primary full" disabled={busy}><QrCode size={18} />{busy ? ar ? "جارٍ إنشاء الرمز…" : "Generating…" : ar ? "إنشاء رمز التفعيل وQR" : "Generate activation code & QR"}</button>
        </form>
        {error && <p className="error" role="alert">{error}</p>}
        {result && <div className="activation-result" role="status">
          <span className="badge"><Check size={14} /> {result.tier === "FAMILY_PRO" ? "Family Pro" : "Clinical SLP"} · {ar ? "جاهز للتفعيل" : "Ready to activate"}</span>
          <img className="activation-qr" width={280} height={280} src={result.qr} alt={ar ? "رمز QR لتفعيل خطتك على الجهاز المرتبط" : "Scan this QR in Voxa to activate the paired installation"} />
          <p>{ar ? "الدخول المدفوع التجريبي حتى " : "Demo access through "}{new Intl.DateTimeFormat(ar ? "ar-EG" : "en", { dateStyle: "medium" }).format(result.expiresAt)}</p>
          <div className="hero-actions"><button type="button" className="button quiet" onClick={async () => { try { await navigator.clipboard.writeText(result.token); setCopied(true); } catch { setError(ar ? "حدّد الرمز أدناه وانسخه يدوياً." : "Select the code below and copy it manually."); } }}>{copied ? <Check size={16} /> : <Copy size={16} />}{copied ? ar ? "تم النسخ" : "Copied" : ar ? "نسخ رمز التفعيل" : "Copy activation code"}</button><a className="button primary" href={result.deepLink}>{ar ? "افتح في فوكسا" : "Open in Voxa"}</a></div>
          <details><summary>{ar ? "عرض رمز التفعيل" : "Show activation code"}</summary><textarea readOnly value={result.token} dir="ltr" aria-label={ar ? "رمز التفعيل" : "Activation code"} /></details>
          <p className="subtle">{ar ? "إذا ظهر «مفتاح غير موثوق»، حدّث إصدار العرض من التطبيق أولاً." : "If the app reports an untrusted key, install the updated demo APK first."}</p>
        </div>}
      </div>
    </div>
    <div className="hero-actions"><Link className="text-link" href={`/${lang}/pricing`}>{ar ? "مقارنة الخطط" : "Compare plan benefits"}</Link><Link className="text-link" href={`/${lang}/clinical`}>{ar ? "اكتشف استخدام المراكز" : "Explore Voxa for centers"}</Link></div>
  </section>;
}
