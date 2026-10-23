import { ShieldCheck } from "lucide-react";
import { CenterExperience } from "./CenterExperience";
import type { Lang } from "./Shell";

export function CenterMarketing({ lang }: { lang: Lang }) {
  const ar = lang === "ar";
  return <section className="section information-page center-page">
    <div className="page-intro">
      <div className="eyebrow">{ar ? "للعيادات ومراكز التخاطب" : "FOR SPEECH & COMMUNICATION CENTERS"}</div>
      <h1>{ar ? <>أثر الجلسة،<br /><em>يمتد إلى البيت.</em></> : <>Let each session<br /><em>reach a little further.</em></>}</h1>
      <p>{ar ? "امنح الأسرة أداة تواصل شخصية وممارسة يومية، وابدأ الجلسة التالية بسياق أوضح عمّا حدث في المنزل. يبقى صوت الطفل على الجهاز." : "Give families a personal communication tool and a way to practice between visits. Start the next session with more context about home activity, while the child’s voice stays on their device."}</p>
    </div>
    <CenterExperience lang={lang} />
    <div className="audience-grid center-benefits">
      <article><div className="eyebrow">{ar ? "لفريق المركز" : "FOR YOUR TEAM"}</div><h2>{ar ? "تركيز أكبر على الجلسة." : "More context. More focused sessions."}</h2><p>{ar ? "ملفات متعددة على جهاز المركز، وبحث سريع عن الطفل. تساعد ملخصات الممارسة والمهام في تحديد النقاط التي تحتاج متابعة، دون جمع تسجيلات الطفل." : "Manage multiple profiles on a clinic device and find a child quickly. Practice and task summaries help the team decide what to revisit, without collecting a child’s recordings."}</p></article>
      <article><div className="eyebrow">{ar ? "للأسرة" : "FOR THE FAMILY"}</div><h2>{ar ? "خطوة واضحة بين كل زيارتين." : "A practical next step after the visit."}</h2><p>{ar ? "ممارسة كلام ومهام ومكافآت على الجهاز، وتقارير يختار ولي الأمر مشاركتها. يواصل الطفل استخدام أصواته المألوفة للتواصل في يومه." : "On-device speech practice, tasks, and rewards keep families involved. Caregivers choose when to share a summary, while the child communicates with sounds already familiar to them."}</p></article>
    </div>
    <div className="privacy-callout"><ShieldCheck /><div><h2>{ar ? "ملخص مفيد، وخصوصية محفوظة." : "Useful summaries. Private voices."}</h2><p>{ar ? "تقارير أرقام وأنشطة، دون صوت أو بصمات صوتية أو ملاحظات خاصة. يختار ولي الأمر التصدير والمشاركة." : "Reports contain activity counts, not recordings, voice embeddings, or private notes. Export and sharing stay under caregiver control."}</p></div></div>
  </section>;
}
