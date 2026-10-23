"use client";
import Link from "next/link";
import { useRef, useState } from "react";
import { ArrowUpRight, Check, FileChartColumn, ShieldCheck, Smartphone, Users } from "lucide-react";
import type { Lang } from "./Shell";

const weeks = [
  { attempts: 24, days: 4, approved: 5, total: 8, bars: [4, 7, 0, 6, 0, 7, 0] },
  { attempts: 38, days: 5, approved: 7, total: 9, bars: [5, 8, 0, 9, 7, 9, 0] },
  { attempts: 52, days: 6, approved: 9, total: 10, bars: [7, 9, 6, 10, 9, 11, 0] },
];
export function CenterExperience({ lang }: { lang: Lang }) {
  const ar = lang === "ar";
  const [week, setWeek] = useState(2);
  const reportDialog = useRef<HTMLDialogElement>(null);
  const data = weeks[week];
  return <>
    <div className="hero-actions"><Link className="button primary" href={`/${lang}/plan-demo?tier=CLINICAL_SLP`}>{ar ? "جرّب Clinical SLP" : "Activate a clinic demo"}<ArrowUpRight size={17} /></Link><button className="button quiet" onClick={() => reportDialog.current?.showModal()}><FileChartColumn size={17} />{ar ? "استعرض تقريراً نموذجياً" : "Preview a progress report"}</button><a className="text-link" href="mailto:voxasupport@gmail.com?subject=Voxa%20center%20pilot">{ar ? "تواصل لتجربة بالمركز" : "Discuss a center pilot"}<ArrowUpRight size={15} /></a></div>
    <div className="center-workflow">
      {[
        [<Smartphone key="s" />, ar ? "١. جهّز جهاز المركز" : "01. Set up a clinic device", ar ? "فعّل Clinical SLP، ثم أنشئ ملفات منفصلة للأطفال وابحث عن الملف المناسب لكل جلسة." : "Activate Clinical SLP, keep separate child profiles, and find the right profile for each session."],
        [<FileChartColumn key="f" />, ar ? "٢. اجعل المتابعة أوضح" : "02. Make follow-ups concrete", ar ? "راجع ملخصاً تشاركه الأسرة: أيام الممارسة والمحاولات وحالة المهام. لا حاجة لإرسال الصوت." : "Review a summary the caregiver shares: practice days, attempts, and task status. Voice recordings are not needed."],
        [<Users key="u" />, ar ? "٣. واصل الممارسة في المنزل" : "03. Keep practice going at home", ar ? "ساعد الأسرة على تحديد أهداف المنزل، ونقل الملف مباشرة بين الأجهزة الموثوقة عند الحاجة." : "Agree on home goals with the caregiver and hand over a working profile directly between trusted nearby devices when needed."],
      ].map(([icon, title, body], i) => <article key={i}>{icon}<h2>{title}</h2><p>{body}</p></article>)}
    </div>
    <dialog ref={reportDialog} className="information-dialog report-dialog" aria-label={ar ? "تقرير متابعة تجريبي" : "Sample progress report"}>
      <button className="button quiet small dialog-close" onClick={() => reportDialog.current?.close()}>{ar ? "إغلاق" : "Close"}</button>
      <section className="sample-report">
      <div className="sample-header"><div><div className="eyebrow">{ar ? "بيانات توضيحية فقط" : "ILLUSTRATIVE DATA ONLY"}</div><h2>{ar ? "بين الجلسات، الصورة أوضح." : "A clearer picture between sessions."}</h2><p>{ar ? "ملف توضيحي A · دون اسم أو تسجيلات أو ملاحظات خاصة" : "Sample profile A · no names, recordings, or private notes"}</p></div><ShieldCheck size={34} /></div>
      <div className="segmented" aria-label={ar ? "أسبوع التقرير" : "Report week"}>{weeks.map((_, i) => <button key={i} aria-pressed={week === i} className={week === i ? "selected" : ""} onClick={() => setWeek(i)}>{ar ? `الأسبوع ${i + 1}` : `Week ${i + 1}`}</button>)}</div>
      <div className="sample-metrics">{[[data.attempts, ar ? "محاولة مسجلة" : "Recorded attempts"], [data.days, ar ? "أيام ممارسة" : "Practice days"], [`${data.approved}/${data.total}`, ar ? "مهام معتمدة" : "Approved tasks"]].map(([n, label]) => <div key={label}><b>{n}</b><span>{label}</span></div>)}</div>
      <div className="sample-chart" aria-label={ar ? "محاولات الممارسة اليومية" : "Daily practice attempts"}>{data.bars.map((n, i) => <div key={i}><span>{n}</span><i style={{ height: `${Math.max(n * 7, 3)}px` }} /><small>{(ar ? ["سبت", "أحد", "اثن", "ثلا", "أرب", "خمي", "جمع"] : ["Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri"])[i]}</small></div>)}</div>
      <p className="notice"><Check size={17} />{ar ? "هذه أرقام توضيحية للنشاط، وليست تقييماً سريرياً. في التطبيق الحالي، ينشئ ولي الأمر التقرير ويختار مشاركته؛ لا يوجد إرسال تلقائي للمركز." : "These are illustrative activity counts, not clinical assessments. In the current app, the caregiver generates and chooses to share a report; automatic center delivery is not enabled."}</p>
    </section></dialog>
  </>;
}
