import Link from "next/link";
import {
  ArrowUpRight,
  AudioLines,
  FileChartColumn,
  ShieldCheck,
  Smartphone,
} from "lucide-react";
import type { Lang } from "./Shell";
export function Clinical({ lang }: { lang: Lang }) {
  const ar = lang === "ar";
  return (
    <section className="section information-page">
      <div className="page-intro">
        <div className="eyebrow">
          {ar ? "بين البيت ومركز الرعاية" : "BETWEEN HOME AND THE CARE TEAM"}
        </div>
        <h1>
          {ar ? (
            <>
              تواصل يومي.
              <br />
              <em>رؤية أوضح معاً.</em>
            </>
          ) : (
            <>
              Everyday connection.
              <br />
              <em>A clearer picture, together.</em>
            </>
          )}
        </h1>
        <p>
          {ar
            ? "فوكسـا يدعم الممارسة بين الجلسات، مع بقاء الصوت على جهاز الطفل."
            : "Voxa supports practice between sessions while keeping a child’s voice on their device."}
        </p>
      </div>
      <div className="clinical-grid">
        {[
          [
            <AudioLines key="a" />,
            ar ? "تعرف شخصي محلي" : "Personal, local recognition",
            ar
              ? "أمثلة صوتية قليلة تربط صوتاً مألوفاً بمعنى محدد."
              : "A small set of examples maps a familiar vocalization to a chosen meaning.",
          ],
          [
            <Smartphone key="b" />,
            ar ? "تسليم قريب بين الأجهزة" : "Nearby device handover",
            ar
              ? "نقل ملف الطفل مباشرة بين الأجهزة الموثوقة دون رفعه إلى الخادم."
              : "Transfer a working profile directly between trusted nearby devices without uploading it to a server.",
          ],
          [
            <FileChartColumn key="c" />,
            ar ? "سياق للممارسة" : "Context for practice",
            ar
              ? "متابعة نشاط التمرين على الجهاز. درجات المطابقة ليست مؤشرات حيوية مثبتة."
              : "Review practice activity on the device. Matching scores are not validated biomarkers.",
          ],
          [
            <ShieldCheck key="d" />,
            ar ? "وصول مضبوط" : "Intentional access",
            ar
              ? "تراخيص موقعة، وعدد أجهزة محدد، وتفعيل بإشراف ولي الأمر."
              : "Signed licenses, explicit device limits, and caregiver-confirmed activation.",
          ],
        ].map(([icon, title, text], i) => (
          <article key={i}>
            {icon}
            <h2>{title}</h2>
            <p>{text}</p>
          </article>
        ))}
      </div>
      <div className="notice">
        {ar
          ? "هذا الإصدار لا يدّعي نتائج سريرية مثبتة أو اعتماداً تنظيمياً. لوحات المؤشرات الحيوية وحزم الأصوات الإضافية تتطلب تطويراً وتحققاً منفصلين."
          : "This preview does not claim validated clinical outcomes or regulatory approval. Biomarker dashboards and additional voice packs require separate development and validation."}
      </div>
      <div className="final-cta">
        <h2>
          {ar
            ? "ابدأ بنطاق واضح لفريقك."
            : "A clear starting point for your team."}
        </h2>
        <p>
          {ar
            ? "جرّب إدارة التراخيص قبل توسيع الاستخدام إلى مركزك."
            : "Explore license management before expanding to a center deployment."}
        </p>
        <Link className="button primary" href={`/${lang}/pricing`}>
          {ar ? "عرض خطة الفرق" : "View the team plan"}
          <ArrowUpRight size={16} />
        </Link>
      </div>
    </section>
  );
}
export function Legal({
  lang,
  kind,
}: {
  lang: Lang;
  kind: "privacy" | "terms";
}) {
  const ar = lang === "ar",
    privacy = kind === "privacy";
  const sections = privacy
    ? [
        [
          ar ? "بيانات تبقى على الجهاز" : "What stays on the device",
          ar
            ? "تسجيلات الطفل ونماذج التعرف الشخصية وسجل التمرين ومهام الطفل محلية. يمكنك اختيار تصدير ملخصات ورفعها لحسابك. لا يقبل رفع التقارير الصوت أو قوالب التعرف أو الملاحظات الخاصة."
            : "Child recordings, recognition templates, and detailed practice and task records stay local. You may choose to export and upload numerical summaries to your account. Report uploads reject audio, recognition templates, and private notes.",
        ],
        [
          ar ? "بيانات الحساب والترخيص" : "Account and license data",
          ar
            ? "يتلقى الخادم هوية حساب ولي الأمر والبريد الإلكتروني والخطة وفترة الاشتراك ومعرف تثبيت عشوائياً. عند تهيئة البريد، تُرسل رسالة تأكيد للعنوان الموثق. لا نستخدم بصمة صوت أو معرف عتاد للتفعيل."
            : "The server receives a caregiver account identifier, email, plan, subscription period, and a random installation identifier. When email delivery is configured, a fulfillment message goes to the authenticated account email. Activation does not use a voiceprint or hardware fingerprint.",
        ],
        [
          ar ? "الدفع" : "Payments",
          ar
            ? "يتم إدخال بيانات الدفع لدى مزود الدفع. يحفظ فوكسـا مراجع المعاملات وحالتها، ولا يحفظ أرقام البطاقات. قد يطلب Paymob اسم الفوترة ورقم الهاتف."
            : "Payment details are entered with the payment provider. Voxa stores transaction references and status, not card numbers. Paymob may require a billing name and phone number.",
        ],
        [
          ar ? "التحكم والاحتفاظ" : "Control and retention",
          ar
            ? "يمكنك حذف بيانات الطفل محلياً من التطبيق. قبل الإطلاق العام، يجب تحديد سياسة احتفاظ وطلبات حذف حسابات وفوترة، وتوفير جهة تواصل للدعم."
            : "Child data can be removed locally in the app. Before public launch, the operator must set retention and account-deletion procedures, billing-record obligations, and a support contact.",
        ],
        [
          ar ? "حالة هذا المستند" : "Status of this document",
          ar
            ? "هذه مسودة للعرض، وليست شهادة امتثال لقوانين خصوصية الأطفال. يتطلب الإطلاق مراجعة للموافقة والبلدان المستهدفة ومزودي الخدمة."
            : "This is a preview privacy notice, not a certification of compliance with children’s privacy laws. Launch requires review of consent, target markets, and service providers.",
        ],
      ]
    : [
        [
          ar ? "أداة مساعدة" : "An assistive tool",
          ar
            ? "فوكسـا يساعد على التواصل والممارسة، ولا يقدم تشخيصاً أو يحل محل الرعاية المتخصصة. أدوات الطوارئ تعتمد على أذونات الجهاز والشبكات والخدمات المتاحة."
            : "Voxa supports communication and practice. It does not diagnose conditions or replace professional care. Emergency actions depend on permissions and available device, carrier, and messaging services.",
        ],
        [
          ar ? "الوصول المدفوع" : "Paid access",
          ar
            ? "تحدد صفحة الدفع الخطة والسعر وفترة الوصول. يتطلب التفعيل اتصالاً أولياً. يظل الترخيص صالحاً محلياً حتى نهايته، ثم فترة سماح سبعة أيام. لا تعني ميزة العمل دون إنترنت صلاحية مدفوعة دائمة."
            : "Checkout specifies the plan, price, and access period. Activation needs an initial connection. A signed license remains locally valid through its paid period and seven-day grace period. Offline use does not mean perpetual paid access.",
        ],
        [
          ar ? "التجديد والإلغاء" : "Renewal and cancellation",
          ar
            ? "تُدار اشتراكات Stripe من بوابة الحساب، ويستمر الوصول حتى نهاية الفترة المدفوعة عند الإلغاء المعتاد. خطط Paymob في هذا الإصدار مدفوعة مسبقاً وتُجدد يدوياً. سياسة الاسترداد النهائية يجب تحديدها قبل البيع العام."
            : "Stripe subscriptions are managed through the account portal; ordinary cancellation retains access through the paid period. Paymob plans in this release are prepaid and renewed manually. The operator must publish its final refund policy before public sales.",
        ],
        [
          ar ? "وضع العرض" : "Demo mode",
          ar
            ? "العرض المحلي لا يحصّل أموالاً ولا يمثل عملية دفع حقيقية. يتطلب التشغيل المباشر حسابات تجار وأسرار خادم وإعداد نطاق الإنتاج."
            : "The local demo charges no money and does not represent a real payment. Live operation requires merchant accounts, server secrets, and a configured production domain.",
        ],
      ];
  return (
    <article className="section legal-page">
      <div className="eyebrow">
        {ar ? "مسودة الإصدار التجريبي" : "PREVIEW NOTICE · OCTOBER 2026"}
      </div>
      <h1>
        {privacy
          ? ar
            ? "خصوصيتك في فوكسـا."
            : "Your privacy with Voxa."
          : ar
            ? "شروط واضحة للتواصل."
            : "Clear terms for connection."}
      </h1>
      {sections.map(([title, text]) => (
        <section key={title}>
          <h2>{title}</h2>
          <p>{text}</p>
        </section>
      ))}
    </article>
  );
}
