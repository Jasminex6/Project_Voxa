import Link from "next/link";
export const metadata = {
  title: "Activate Voxa",
  robots: { index: false, follow: false },
};
export default async function Activate({
  searchParams,
}: {
  searchParams: Promise<{ code?: string }>;
}) {
  const { code } = await searchParams;
  const valid = code && /^[A-Za-z0-9_-]{32,128}$/.test(code);
  return (
    <main className="section narrow auth-page">
      <h1>Activate your Voxa device.</h1>
      <p>
        Open Voxa on Android to confirm this activation as caregiver. A
        connection is needed once to pair your installation.
      </p>
      {valid ? (
        <a className="button primary" href={`voxa://activate?code=${code}`}>
          Open Voxa &amp; activate
        </a>
      ) : (
        <p className="error">This activation link is invalid.</p>
      )}
      <Link className="text-link" href="/en/dashboard">
        Return to your account
      </Link>
    </main>
  );
}
