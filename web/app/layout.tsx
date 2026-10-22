import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata = {
  title: {
    default: "Voxa — Every sound deserves to be understood",
    template: "%s · Voxa",
  },
  description:
    "Personal, on-device Arabic communication for non-speaking and minimally speaking children. Explore Voxa plans and manage your devices.",
  openGraph: {
    title: "Voxa — Their voice. Their way.",
    description: "Personal communication, on your device.",
  },
};
export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body>{children}</body>
    </html>
  );
}
