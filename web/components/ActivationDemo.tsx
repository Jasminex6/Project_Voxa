"use client";
import Link from "next/link";
import { useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { renderDemoQr } from "@/lib/demo-qr";
import { Check, Copy, Download, KeyRound, Maximize2, QrCode, ShieldCheck, Smartphone } from "lucide-react";
import { api } from "@/lib/browser";
import type { PaidTier } from "@/lib/catalog";
import type { Lang } from "./Shell";

type DemoCode = { code: string; deepLink: string; tier: PaidTier };
export function ActivationDemo({ lang }: { lang: Lang }) {
  const ar = lang === "ar";
  const params = useSearchParams();
  const [tier, setTier] = useState<PaidTier>(params.get("tier") === "CLINICAL_SLP" ? "CLINICAL_SLP" : "FAMILY_PRO");
  const [busy, setBusy] = useState(false), [error, setError] = useState(""), [copied, setCopied] = useState(false);
  const [result, setResult] = useState<(DemoCode & { qr: string }) | null>(null);
  const largeQr = useRef<HTMLDialogElement>(null);
  async function generate(event: React.FormEvent) {
    event.preventDefault(); setError(""); setBusy(true); setResult(null); setCopied(false);
    try {
      const response = await api<DemoCode>("demo/activate", { tier });
      const qr = await renderDemoQr(window.location.origin, response.code);
      setResult({ ...response, qr });
    } catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }
  async function downloadPool() {
    try {
      const { codes } = await api<{ codes: DemoCode[] }>("demo/codes");
      const file = new Blob(["plan,code\n" + codes.map(row => `${row.tier},${row.code}`).join("\n")], { type: "text/csv;charset=utf-8" });
      const url = URL.createObjectURL(file), a = document.createElement("a");
      a.href = url; a.download = "voxa-100-demo-codes.csv"; a.click(); URL.revokeObjectURL(url);
    } catch (e) { setError((e as Error).message); }
  }
  return <section className="section activation-page">
    <div className="page-intro"><div className="eyebrow"><KeyRound size={16} />{ar ? "رمز واحد. وخطتك جاهزة." : "ONE CODE. YOUR PLAN IS READY."}</div>
      <h1>{ar ? "اختر. امسح. فعّل." : <>Choose. Scan.<br /><em>Start using Voxa.</em></>}</h1>
      <p>{ar ? "اختر الخطة وأنشئ رمزاً قصيراً. امسح QR داخل التطبيق أو اكتب الرمز، دون معرّفات أجهزة أو حسابات أو بطاقات دفع." : "Choose a plan and generate a short code. Scan its QR in Voxa or type the code. No device IDs, accounts, or payment cards."}</p></div>
    <div className="activation-layout"><div className="activation-steps">
      {[
        [ar ? "اختر الخطة" : "Choose your plan", ar ? "Family Pro للأسرة أو Clinical SLP لتجربة أدوات المركز." : "Family Pro for family use, or Clinical SLP to try the clinic tools."],
        [ar ? "امسح أو اكتب" : "Scan or type", ar ? "افتح خطتي ← تفعيل رمز. امسح QR أو اكتب رمز VOXA الظاهر بجواره." : "In My plan → Activate a code, scan the QR or enter the VOXA code shown beside it."],
        [ar ? "أكّد وابدأ" : "Confirm and start", ar ? "أدخل رمز ولي الأمر. يتصل التطبيق مرة واحدة للتفعيل، ثم تعمل مزايا الخطة دون اتصال." : "Confirm your caregiver PIN. The app connects once to activate, then verifies plan access offline."],
      ].map(([title, body], i) => <article key={i}><span className="step-number">0{i + 1}</span><div><h2>{title}</h2><p>{body}</p></div></article>)}
      <div className="notice"><ShieldCheck size={18} />{ar ? "عرض هاكاثون مجاني: ١٠٠ رمز قابل لإعادة الاستخدام على أجهزة الاختبار؛ ٥٠ لكل خطة. كل تفعيل يمنح ٧ أيام مع فترة السماح دون اتصال. لا نطلب بيانات الطفل أو تسجيلاته." : "Free hackathon demo: 100 reusable codes, 50 per plan, for your test devices. Each activation grants seven days plus offline grace. No child information or recordings are requested."}</div>
      <button className="button quiet" type="button" onClick={downloadPool}><Download size={16} />{ar ? "تنزيل رموز العرض الـ١٠٠" : "Download all 100 demo codes"}</button>
    </div><div className={`activation-panel${result ? " has-result" : ""}`}><div className="activation-controls"><form className="form" onSubmit={generate}>
      <div className="panel-heading"><span className="page-icon"><Smartphone /></span><div><h2>{ar ? "رمز تفعيل خطتك" : "Your plan activation"}</h2><p>{ar ? "استخدم إصدار العرض المحدّث من تطبيق فوكسا." : "Use the updated Voxa demo APK."}</p></div></div>
      <label>{ar ? "الخطة" : "Plan"}<select value={tier} onChange={e => { setTier(e.target.value as PaidTier); setResult(null); }}><option value="FAMILY_PRO">Family Pro</option><option value="CLINICAL_SLP">Clinical SLP</option></select></label>
      <button className="button primary" disabled={busy}><QrCode size={18} />{busy ? ar ? "جارٍ إنشاء الرمز…" : "Generating…" : ar ? "إنشاء الرمز وQR" : "Generate code & QR"}</button>
    </form>{error && <p className="error" role="alert">{error}</p>}
    {result && <div className="activation-code-actions" role="status"><span className="badge"><Check size={14} />{result.tier === "FAMILY_PRO" ? "Family Pro" : "Clinical SLP"} · {ar ? "جاهز للتفعيل" : "Ready to activate"}</span>
      <code className="short-activation-code" dir="ltr">{result.code}</code>
      <div className="hero-actions"><button type="button" className="button quiet" onClick={async () => { try { await navigator.clipboard.writeText(result.code); setCopied(true); } catch { setError(ar ? "حدّد الرمز وانسخه يدوياً." : "Select the short code and copy it manually."); } }}>{copied ? <Check size={16} /> : <Copy size={16} />}{copied ? ar ? "تم النسخ" : "Copied" : ar ? "نسخ الرمز" : "Copy code"}</button><a className="button primary" href={result.deepLink}>{ar ? "افتح في فوكسا" : "Open in Voxa"}</a></div>
      <p className="subtle">{ar ? "رمز مجاني قابل للاستخدام على أجهزة العرض الأخرى." : "Free demo code. Reuse it on your other test devices."}</p>
    </div>}</div>
    <div className="activation-result">{result ? <>
      <img className="activation-qr" width={360} height={360} src={result.qr} alt={ar ? "رمز QR لتفعيل الخطة" : "Voxa plan activation QR"} />
      <button className="text-link" type="button" onClick={() => largeQr.current?.showModal()}><Maximize2 size={15} />{ar ? "تكبير QR" : "Enlarge QR"}</button>
      <dialog ref={largeQr} className="qr-dialog"><button className="button quiet" onClick={() => largeQr.current?.close()}>{ar ? "إغلاق" : "Close"}</button><img src={result.qr} alt={ar ? "QR مكبّر" : "Enlarged activation QR"} /><code dir="ltr">{result.code}</code></dialog>
    </> : <div className="activation-placeholder"><QrCode size={48} strokeWidth={1.2} /><h3>{ar ? "رمزك يظهر هنا" : "Your QR appears here"}</h3><p>{ar ? "أنشئ الرمز، ثم امسحه من «خطتي» داخل فوكسا." : "Generate a code, then scan it from My plan in Voxa."}</p></div>}</div></div></div>
    <div className="hero-actions"><Link className="text-link" href={`/${lang}/pricing`}>{ar ? "مقارنة الخطط" : "Compare plan benefits"}</Link><Link className="text-link" href={`/${lang}/clinical`}>{ar ? "استخدام المراكز" : "Explore Voxa for centers"}</Link></div>
  </section>;
}
