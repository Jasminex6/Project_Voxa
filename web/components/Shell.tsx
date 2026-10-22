"use client";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { ArrowUpRight, AudioLines, Globe2, Menu, X } from "lucide-react";
export type Lang = "en" | "ar";
export function Shell({
  lang,
  children,
}: {
  lang: Lang;
  children: React.ReactNode;
}) {
  const ar = lang === "ar",
    pathname = usePathname();
  const [open, setOpen] = useState(false);
  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = ar ? "rtl" : "ltr";
  }, [lang, ar]);
  const query = useSearchParams().toString();
  const other =
    pathname.replace(/^\/(en|ar)/, ar ? "/en" : "/ar") +
    (query ? `?${query}` : "");
  return (
    <div lang={lang} dir={ar ? "rtl" : "ltr"}>
      <a className="skip" href="#content">
        {ar ? "انتقل إلى المحتوى" : "Skip to content"}
      </a>
      <header className="header">
        <div className="nav-wrap">
          <Link className="brand" href={`/${lang}`} aria-label="Voxa home">
            <span className="brand-icon">
              <AudioLines size={23} />
            </span>
            voxa<span className="brand-dot">.</span>
          </Link>
          <nav
            className={open ? "nav open" : "nav"}
            aria-label={ar ? "القائمة الرئيسية" : "Main navigation"}
          >
            <Link onClick={() => setOpen(false)} href={`/${lang}#how`}>
              {ar ? "كيف يعمل" : "How it works"}
            </Link>
            <Link onClick={() => setOpen(false)} href={`/${lang}/pricing`}>
              {ar ? "الخطط" : "Plans"}
            </Link>
            <Link onClick={() => setOpen(false)} href={`/${lang}/clinical`}>
              {ar ? "للمراكز" : "For centers"}
            </Link>
            <a href="mailto:voxasupport@gmail.com" onClick={() => setOpen(false)}>
              {ar ? "الدعم" : "Support"}
            </a>
          </nav>
          <div className="nav-actions">
            <Link className="language" href={other}>
              <Globe2 size={15} />
              {ar ? "English" : "العربية"}
            </Link>
            <Link className="button small quiet" href={`/${lang}/dashboard`}>
              {ar ? "حسابي" : "My account"}
              <ArrowUpRight size={14} />
            </Link>
            <button
              className="icon-button menu"
              aria-label={ar ? "القائمة" : "Menu"}
              aria-expanded={open}
              onClick={() => setOpen(!open)}
            >
              {open ? <X /> : <Menu />}
            </button>
          </div>
        </div>
      </header>
      <main id="content">{children}</main>
      <footer className="footer">
        <div className="footer-main">
          <Link className="brand" href={`/${lang}`}>
            <AudioLines />
            voxa.
          </Link>
          <p>
            {ar
              ? "صوت طفلك. عالمه. بطريقته."
              : "Their voice. Their world. Their way."}
          </p>
          <div>
            <Link href={`/${lang}/privacy`}>{ar ? "الخصوصية" : "Privacy"}</Link>
            <Link href={`/${lang}/terms`}>{ar ? "الشروط" : "Terms"}</Link>
            <a href="mailto:voxasupport@gmail.com">
              {ar ? "الدعم" : "Support"} · voxasupport@gmail.com
            </a>
          </div>
        </div>
        <div className="footer-bottom">
          <span>
            © {new Date().getFullYear()} Voxa ·{" "}
            {ar ? "نموذج الهاكاثون" : "Hackathon preview"}
          </span>
          <span>
            {ar
              ? "مصمم بعناية. يعمل على جهازك."
              : "Built with care. Runs on your device."}
          </span>
        </div>
      </footer>
    </div>
  );
}
