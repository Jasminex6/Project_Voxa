import Link from "next/link";
export default function NotFound() {
  return (
    <main className="section not-found">
      <div className="eyebrow">VOXA · 404</div>
      <h1>This page wandered off.</h1>
      <p>Let’s get you back to a familiar place.</p>
      <Link className="button primary" href="/en">
        Back to Voxa
      </Link>
    </main>
  );
}
