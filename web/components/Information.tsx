import type { Lang } from "./Shell";
export { CenterMarketing as Clinical } from "./CenterMarketing";
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
