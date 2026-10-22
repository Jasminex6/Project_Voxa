import { Suspense } from "react";
import { notFound } from "next/navigation";
import type { Metadata } from "next";
import { Shell } from "@/components/Shell";
import { Landing } from "@/components/Landing";
import { Pricing } from "@/components/Pricing";
import { Checkout, Dashboard, SignIn } from "@/components/Account";
import { Clinical, Legal } from "@/components/Information";
type Params = { params: Promise<{ lang: string; slug?: string[] }> };
export async function generateMetadata({ params }: Params): Promise<Metadata> {
  const { lang, slug } = await params;
  const titles: Record<string, string> = {
    pricing: lang === "ar" ? "الخطط" : "Plans",
    dashboard: lang === "ar" ? "حسابي" : "Your account",
    privacy: lang === "ar" ? "الخصوصية" : "Privacy",
    terms: lang === "ar" ? "الشروط" : "Terms",
    clinical: lang === "ar" ? "لفرق الرعاية" : "For care teams",
    signin: lang === "ar" ? "تسجيل الدخول" : "Sign in",
  };
  return {
    title: titles[slug?.[0] ?? ""] ?? "Voxa — Their voice. Their way.",
    robots:
      slug?.[0] === "dashboard" || slug?.[0] === "checkout"
        ? { index: false, follow: false }
        : undefined,
  };
}
export default async function Page({ params }: Params) {
  const { lang, slug } = await params;
  if (lang !== "en" && lang !== "ar") notFound();
  const route = slug?.join("/") ?? "";
  let content;
  switch (route) {
    case "":
      content = <Landing lang={lang} />;
      break;
    case "pricing":
      content = <Pricing lang={lang} />;
      break;
    case "dashboard":
      content = <Dashboard lang={lang} />;
      break;
    case "signin":
      content = <SignIn lang={lang} />;
      break;
    case "checkout/demo":
      content = <Checkout lang={lang} demo />;
      break;
    case "checkout/success":
      content = <Checkout lang={lang} />;
      break;
    case "clinical":
      content = <Clinical lang={lang} />;
      break;
    case "privacy":
    case "terms":
      content = <Legal lang={lang} kind={route} />;
      break;
    default:
      notFound();
  }
  return (
    <Shell lang={lang}>
      <Suspense fallback={<div className="section loading">Loading…</div>}>
        {content}
      </Suspense>
    </Shell>
  );
}
