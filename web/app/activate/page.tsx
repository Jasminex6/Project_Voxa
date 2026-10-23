import Link from "next/link";
import { activationDeepLink } from "@/lib/activation-link";
export const metadata = {
  title: "Activate Voxa",
  robots: { index: false, follow: false },
};
export default async function Activate({
  searchParams,
}: {
  searchParams: Promise<{ code?: string | string[]; token?: string | string[] }>;
}) {
  const deepLink = activationDeepLink(await searchParams);
  return (
    <main className="section narrow auth-page">
      <h1>Activate your Voxa device.</h1>
      <p>
        Open Voxa on Android to confirm this activation as caregiver. A
        connection is needed once to pair your installation.
      </p>
      {deepLink ? (
        <a className="button primary" href={deepLink}>
          Open Voxa &amp; activate
        </a>
      ) : (
        <p className="error">This activation link is invalid.</p>
      )}
      <Link className="text-link" href="/en/plan-demo">
        Generate a demo code
      </Link>
    </main>
  );
}
