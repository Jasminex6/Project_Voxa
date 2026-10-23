"use client";
import Link from "next/link";
import { motion, useReducedMotion } from "framer-motion";
import {
  ArrowRight,
  ArrowUpRight,
  AudioLines,
  Check,
  CheckCheck,
  ChevronRight,
  Download,
  Heart,
  LockKeyhole,
  ShieldCheck,
  Smartphone,
  Sparkles,
  Volume2,
  WifiOff,
} from "lucide-react";
import type { Lang } from "./Shell";

export function Landing({ lang }: { lang: Lang }) {
  const ar = lang === "ar",
    reduce = useReducedMotion();
  const enter = reduce
    ? {}
    : {
        initial: { opacity: 0, y: 20 },
        animate: { opacity: 1, y: 0 },
        transition: { duration: 0.65 },
      };
  return (
    <>
      <section className="hero section">
        <motion.div {...enter} className="hero-copy">
          <div className="eyebrow">
            <span className="live-dot" />
            {ar ? "تواصل يبدأ من طفلك" : "COMMUNICATION, ON THEIR TERMS"}
          </div>
          <h1>
            {ar ? (
              <>
                كل صوت
                <br />
                يستحق أن <em>يُفهم.</em>
              </>
            ) : (
              <>
                Every sound
                <br />
                deserves to
                <br />
                be <em>understood.</em>
              </>
            )}
          </h1>
          <p className="lead">
            {ar
              ? "فوكسـا يحوّل أصوات طفلك المألوفة إلى كلمات عربية واضحة. تواصل شخصي يعمل على جهازك، حتى دون إنترنت."
              : "Voxa turns your child’s familiar sounds into clear Arabic words. Personal communication that stays on your device—even when life goes offline."}
          </p>
          <div className="hero-actions">
            <Link className="button primary" href={`/${lang}/pricing`}>
              {ar ? "اختر خطتك" : "Find your plan"}
              <ArrowUpRight size={18} />
            </Link>
            <Link className="text-link" href={`/${lang}/clinical`}>{ar ? "فوكسا للمراكز" : "Voxa for centers"}<ArrowRight size={16} /></Link>
          </div>
          <div className="trust-row">
            <span>
              <WifiOff size={15} />
              {ar ? "تعرف محلي على الأصوات" : "On-device recognition"}
            </span>
            <span>
              <ShieldCheck size={15} />
              {ar ? "خصوصية من البداية" : "Privacy by design"}
            </span>
          </div>
        </motion.div>
        <motion.div
          {...enter}
          transition={{ duration: 0.7, delay: 0.15 }}
          className="hero-visual"
          aria-label={ar ? "نموذج واجهة التطبيق" : "Illustrative app preview"}
        >
          <div className="orbit orbit-one" />
          <div className="orbit orbit-two" />
          <div className="floating-note note-top">
            <span className="note-icon">
              <WifiOff size={17} />
            </span>
            <div>
              <b>{ar ? "دائماً بالقرب منك" : "Always within reach"}</b>
              <small>
                {ar
                  ? "صوت طفلك يبقى على جهازك"
                  : "Your child’s voice stays local"}
              </small>
            </div>
          </div>
          <div className="phone">
            <div className="phone-notch" />
            <div className="phone-top">
              <span>9:41</span>
              <span>● ▰</span>
            </div>
            <div className="phone-greeting">
              <span>{ar ? "مساحة للتواصل" : "A little space to connect"}</span>
              <b>{ar ? "أهلاً، نور ☀" : "Hello, Nour ☀"}</b>
            </div>
            <div className="phone-caption">
              {ar ? "نموذج توضيحي" : "ILLUSTRATIVE PREVIEW"}
            </div>
            <div className="voice-orb">
              <AudioLines size={47} strokeWidth={1.2} />
            </div>
            <div className="listening">
              <span className="live-dot" />
              {ar ? "أنا بسمعك" : "I’m listening"}
            </div>
            <div className="waveform" aria-hidden="true">
              {[
                12, 20, 35, 19, 45, 65, 35, 22, 51, 77, 44, 29, 60, 43, 20, 34,
                15, 23,
              ].map((h, i) => (
                <i key={i} style={{ height: h }} />
              ))}
            </div>
            <div className="translation">
              <span>
                <CheckCheck size={13} />
                {ar ? "وصلت رسالتك" : "Message understood"}
              </span>
              <strong lang="ar" dir="rtl">
                أنا عايز مياه
              </strong>
              <small>“I want water”</small>
              <span className="translation-play">
                <Volume2 size={17} />
              </span>
            </div>
            <div className="phone-tabs">
              <AudioLines />
              <Heart />
              <Sparkles />
              <Smartphone />
            </div>
          </div>
          <div className="floating-note note-bottom">
            <span className="note-heart">♡</span>
            <div>
              <b>
                {ar
                  ? "صوت مألوف. معنى واضح."
                  : "A familiar sound. A clear meaning."}
              </b>
              <small>
                {ar
                  ? "لحظات صغيرة، تواصل أكبر"
                  : "Small moments, more connection"}
              </small>
            </div>
          </div>
        </motion.div>
      </section>
      <div className="principles">
        <span>{ar ? "شخصي بطبيعته" : "Personal by nature"}</span>
        <i />
        <span>{ar ? "العربية أولاً" : "Arabic at heart"}</span>
        <i />
        <span>{ar ? "خصوصيتك على جهازك" : "Private on your device"}</span>
        <i />
        <span>{ar ? "مصمم للعائلة" : "Made for real family life"}</span>
      </div>
      <section id="how" className="section how">
        <div className="section-intro">
          <div className="eyebrow">
            {ar ? "من صوته إلى عالمه" : "FROM THEIR SOUND TO THEIR WORLD"}
          </div>
          <h2>
            {ar ? (
              <>
                التواصل يبدأ
                <br />
                بما يعرفه طفلك.
              </>
            ) : (
              <>
                Connection starts
                <br />
                with what comes naturally.
              </>
            )}
          </h2>
          <p>
            {ar
              ? "لا يحتاج طفلك إلى تعلم طريقة جديدة ليُسمع. ابدأ بالأصوات التي يستخدمها بالفعل."
              : "Your child doesn’t need to learn a new way to be heard. Start with the sounds they already use."}
          </p>
        </div>
        <div className="steps">
          {[
            [
              "01",
              ar ? "تعلّم صوته" : "Learn their sounds",
              ar
                ? "سجّل أمثلة قصيرة لصوت مألوف، واختر المعنى معاً."
                : "Record short examples of a familiar sound and choose its meaning together.",
            ],
            [
              "02",
              ar ? "افهم المعنى" : "Understand the meaning",
              ar
                ? "يطابق محرك فوكسـا الصوت مع الأمثلة المحفوظة على الجهاز."
                : "Voxa matches the sound to examples saved on the device.",
            ],
            [
              "03",
              ar ? "دع التواصل يستمر" : "Let connection happen",
              ar
                ? "يشغّل التطبيق العبارة العربية المرتبطة بالصوت. دون انتظار السحابة."
                : "The app speaks the associated Arabic phrase. No cloud round trip needed.",
            ],
          ].map(([n, title, text]) => (
            <article className="step" key={n}>
              <span>{n}</span>
              <div>
                <h3>{title}</h3>
                <p>{text}</p>
              </div>
              <ChevronRight size={19} />
            </article>
          ))}
        </div>
      </section>
      <section className="section audience-section">
        <div className="section-intro"><div className="eyebrow">{ar ? "أداة واحدة، أثر في كل يوم" : "ONE TOOL. DIFFERENT EVERYDAY BENEFITS."}</div><h2>{ar ? "حول الطفل، مساحة للتواصل." : "A little more connection, for everyone."}</h2></div>
        <div className="audience-grid">
          <article><Heart /><h3>{ar ? "للطفل" : "For the child"}</h3><p>{ar ? "تواصل بأصواته المألوفة، وكلمات عربية واضحة، دون الحاجة إلى اتصال دائم." : "Communicate through familiar sounds and clear Arabic phrases, with on-device recognition that works offline."}</p></article>
          <article><Smartphone /><h3>{ar ? "للأسرة" : "For the family"}</h3><p>{ar ? "تواصل يومي وممارسة كلام ومهام ومكافآت، مع بقاء التسجيلات على جهاز الأسرة." : "Bring communication, speech practice, tasks, and rewards into daily life, while recordings stay on the family’s device."}</p><Link className="text-link" href={`/${lang}/pricing`}>{ar ? "خطط الأسرة" : "Explore family plans"}<ArrowRight size={15} /></Link></article>
          <article><ShieldCheck /><h3>{ar ? "للمركز" : "For the center"}</h3><p>{ar ? "ملفات منفصلة للأطفال، وتسليم آمن بين الأجهزة، وملخصات تشاركها الأسرة لتوضيح الممارسة بين الجلسات." : "Separate child profiles, nearby device handover, and caregiver-shared summaries bring home practice into the next conversation."}</p><Link className="text-link" href={`/${lang}/clinical`}>{ar ? "اكتشف سير عمل المركز" : "See the center workflow"}<ArrowRight size={15} /></Link></article>
        </div>
      </section>
      <section className="section privacy-section">
        <div className="privacy-emblem">
          <LockKeyhole size={36} strokeWidth={1.2} />
          <span>ON DEVICE</span>
        </div>
        <div>
          <div className="eyebrow">
            {ar ? "الخصوصية ليست إضافة" : "PRIVACY IS PART OF THE FOUNDATION"}
          </div>
          <h2>
            {ar
              ? "لحظاته الخاصة،\nتبقى خاصة."
              : "Their private moments\nstay private."}
          </h2>
          <p>
            {ar
              ? "أصوات الطفل ونماذج التعرف تبقى محلياً. لا يتطلب التواصل الأساسي اتصالاً أو اشتراكاً نشطاً. يستخدم التفعيل بيانات الترخيص فقط."
              : "Voice samples and recognition templates stay local. Core communication does not require a connection or an active subscription. Activation exchanges license data only."}
          </p>
          <Link className="text-link" href={`/${lang}/privacy`}>
            {ar ? "اقرأ عن البيانات" : "Understand the data flow"}
            <ArrowRight size={16} />
          </Link>
        </div>
      </section>
      <section className="section final-cta">
        <div className="eyebrow">
          {ar ? "مساحة أكبر للتواصل" : "MORE ROOM FOR CONNECTION"}
        </div>
        <h2>{ar ? "ابدأ بما هو مألوف." : "Start with what’s familiar."}</h2>
        <p>
          {ar
            ? "جرّب فوكسـا، واختر متى تحتاج عائلتك إلى المزيد."
            : "Try Voxa. Choose more when your family is ready."}
        </p>
        <div className="hero-actions">
          <Link href={`/${lang}/pricing`} className="button primary">
            {ar ? "استكشف الخطط" : "Explore the plans"}
            <ArrowUpRight size={17} />
          </Link>
          {process.env.NEXT_PUBLIC_ANDROID_DOWNLOAD_URL ? (
            <a
              className="button quiet"
              href={process.env.NEXT_PUBLIC_ANDROID_DOWNLOAD_URL}
            >
              <Download size={16} />
              Android
            </a>
          ) : (
            <span className="subtle">
              {ar
                ? "نسخة Android متاحة من فريق الهاكاثون"
                : "Android preview available from the hackathon team"}
            </span>
          )}
        </div>
      </section>
    </>
  );
}
