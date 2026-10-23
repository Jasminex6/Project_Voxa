"use client";
import Link from "next/link";
import { useRef } from "react";
import { ArrowUpRight, FileChartColumn, Smartphone, Users } from "lucide-react";
import type { Lang } from "./Shell";
import { ReportReview } from "./ReportReview";
import { reportV2Schema } from "@/lib/report";
import example from "@/lib/report-example.json";

const sample = reportV2Schema.parse(example);
export function CenterExperience({ lang }: { lang: Lang }) {
  const ar = lang === "ar";
  const reportDialog = useRef<HTMLDialogElement>(null);
  return (
    <>
      <div className="hero-actions">
        <Link
          className="button primary"
          href={`/${lang}/plan-demo?tier=CLINICAL_SLP`}
        >
          {ar ? "جرّب Clinical SLP" : "Activate a clinic demo"}
          <ArrowUpRight size={17} />
        </Link>
        <button
          className="button quiet"
          onClick={() => reportDialog.current?.showModal()}
        >
          <FileChartColumn size={17} />
          {ar ? "استعرض تقريراً نموذجياً" : "Preview a progress report"}
        </button>
        <a
          className="text-link"
          href="mailto:voxasupport@gmail.com?subject=Voxa%20center%20pilot"
        >
          {ar ? "تواصل لتجربة بالمركز" : "Discuss a center pilot"}
          <ArrowUpRight size={15} />
        </a>
      </div>
      <div className="center-workflow">
        {[
          [
            <Smartphone key="s" />,
            ar ? "١. جهّز جهاز المركز" : "01. Set up a clinic device",
            ar
              ? "فعّل Clinical SLP، ثم أنشئ ملفات منفصلة للأطفال وابحث عن الملف المناسب لكل جلسة."
              : "Activate Clinical SLP, keep separate child profiles, and find the right profile for each session.",
          ],
          [
            <FileChartColumn key="f" />,
            ar ? "٢. اجعل المتابعة أوضح" : "02. Make follow-ups concrete",
            ar
              ? "راجع ملخصاً تشاركه الأسرة: أيام الممارسة والمحاولات وحالة المهام. لا حاجة لإرسال الصوت."
              : "Review a summary the caregiver shares: practice days, attempts, and task status. Voice recordings are not needed.",
          ],
          [
            <Users key="u" />,
            ar
              ? "٣. واصل الممارسة في المنزل"
              : "03. Keep practice going at home",
            ar
              ? "ساعد الأسرة على تحديد أهداف المنزل، ونقل الملف مباشرة بين الأجهزة الموثوقة عند الحاجة."
              : "Agree on home goals with the caregiver and hand over a working profile directly between trusted nearby devices when needed.",
          ],
        ].map(([icon, title, body], i) => (
          <article key={i}>
            {icon}
            <h2>{title}</h2>
            <p>{body}</p>
          </article>
        ))}
      </div>
      <dialog
        ref={reportDialog}
        className="information-dialog report-dialog"
        aria-label={ar ? "تقرير متابعة تجريبي" : "Sample progress report"}
      >
        <button
          className="button quiet small dialog-close"
          onClick={() => reportDialog.current?.close()}
        >
          {ar ? "إغلاق" : "Close"}
        </button>
        <ReportReview report={sample} lang={lang} sample />
        <div className="review-downloads">
          <a
            className="button quiet small"
            href={`/demo-reports/voxa-review-${lang}.pdf`}
            download
          >
            {ar ? "تنزيل التقرير النموذجي PDF" : "Download sample PDF"}
          </a>
          <p>
            {ar
              ? "بيانات خيالية؛ يختار ولي الأمر مشاركة التقرير. لا إرسال تلقائي للمركز."
              : "Fictional data. Caregivers choose to share; automatic center delivery is not enabled."}
          </p>
        </div>
      </dialog>
    </>
  );
}
