# Voxa web and activation bridge

This Next.js application includes a bilingual storefront, account/license dashboard, report templates, Stripe and Paymob adapters, and an explicit local demo. It is kept in the same branch as the Android licensing implementation.

## Hosted storefront

Live URL: [English](https://voxa-web-pi.vercel.app/en) / [العربية](https://voxa-web-pi.vercel.app/ar). Vercel project: `jazz16/voxa-web`. The site markets family and center benefits, includes an interactive synthetic center report, and generates short reusable demonstration codes and QR codes that redeem for device-bound licenses. No real payments are collected.

Deployments are manual from this `web` folder with `vercel deploy --prod`. `.vercelignore` excludes local keys, environment files, and demo data. The ignored `.vercel/` folder links this checkout to the project. The public pages and configuration were checked without Vercel authentication.

### Pages and layout

| Page | Purpose |
|---|---|
| `/en` / `/ar` | Service introduction, family and center benefits, and the scrolling How it works explanation. |
| `/{lang}/pricing` | Free, Family Pro, and Clinical SLP benefits, EGP/SAR prices, billing-period controls, and activation links. FAQs open in a dialog. |
| `/{lang}/clinical` | Center workflow, family/team benefits, a clinic activation link, and an interactive illustrative report in a dialog. |
| `/{lang}/plan-demo` | Plan selection, short code and QR generation, code copying, an Open in Voxa link, QR enlargement, and a 100-code CSV download. |

Pricing, center benefits, and activation were checked at 1280×720 with their key information and compact footer visible without scrolling. The activation form uses a compact Generate button, with the code and its actions beside the QR (on its left in English). Arabic mirrors the layout. Smaller screens retain natural scrolling instead of clipping information. Support links open `mailto:voxasupport@gmail.com`, a demo contact placeholder.

## Hosted plan activation demo

1. Open `/en/plan-demo` or `/ar/plan-demo`, choose Family Pro or Clinical SLP, and generate a short code and QR. No device ID is requested.
2. In the updated demo APK, open Profile → My plan → Activate a code and scan the QR, or type its short `VOXA-F-...` / `VOXA-C-...` code.
3. Confirm the caregiver PIN. The app connects once to redeem the code, automatically supplies its installation ID, and receives a signed, device-bound license. Verification and cached access then work offline for seven days plus the normal seven-day grace period.

The demo has 100 stable reusable codes, 50 per plan. The same code can be used on multiple test phones. Download all codes as CSV from the activation page. They are intentionally public hackathon codes, not purchased seats or single-use paid exchange codes. Changing the plan letter or checksum causes redemption to fail. The existing paid flow remains separate and enforces its own ownership/seat limits.

QRs contain a short HTTPS activation URL rather than a dense signed token. Voxa scans that URL directly; regular phone cameras can open the activation page and its “Open Voxa” link. The landing page also supports earlier device-bound token links. Pixel-decoding tests cover 240, 360, and 640px. A focused Android test decodes actual QR fixtures with an independent decoder on the Samsung, redeems their codes on the hosted site, and verifies both tiers without changing the family's data, PIN, or license.

A separate signer is trusted only by debug APKs; release builds do not accept demo licenses. `VOXA_PUBLIC_DEMO`, `VOXA_PUBLIC_DEMO_KEY_ID`, and `VOXA_PUBLIC_DEMO_PRIVATE_KEY` enable the endpoint. The code pool is derived server-side from this key, so it persists across deploys without a database; rotating the key changes the pool. No payment, customer account, or child records are stored by this demonstration.

Run `node scripts/setup-hosted-demo.mjs --configure-vercel` after Vercel sign-in to preserve/configure the ignored private key and export only its public verification key. Never commit or upload the private file. `npx tsx scripts/hosted-demo-fixture.ts` regenerates public synthetic interoperability and QR image fixtures.

Build the connected-phone demo with:

```powershell
cd ../Voxa
.\gradlew.bat :app:assembleDebug -PVOXA_WEB_URL=https://voxa-web-pi.vercel.app
```

The center report is interactive sample data, not a live center dashboard. Actual summaries remain caregiver-generated and deliberately shared. Automatic weekly delivery, appointments, and billing are outside this preview.

## Run the local demo

Use Node 22.19+ and Java 21 for Android.

```powershell
cd web
npm ci
npm run setup:demo
npm run dev
```

Open `http://127.0.0.1:3000/en` or `/ar`. Select Family Pro, simulate checkout, create an activation code, and view the QR. The account is a signed local demo session, not a live customer account. No card details or real payment are requested.

`setup:demo` generates an ignored private key in the repository's `.local/`, an ignored `web/.env.local`, a public debug verification key in `config/`, a signed interoperability fixture, and public reference-audio copies. Public fixtures are safe to commit. Private keys, environment files, demo orders, and caches are ignored. Re-running setup preserves the private key and refuses to overwrite a non-demo environment. Fresh developer checkouts must run setup before Android licensing tests.

The demo JSON store is under `web/.local/demo-store.json`. Keep it private and outside source control. The demo is intentionally restricted to localhost; do not publish it as a payment service.

## Activate the Android debug build

The debug app trusts the generated debug public key and defaults to `http://127.0.0.1:3000` for activation. Reverse the port for an emulator or USB-connected test phone:

```powershell
adb reverse tcp:3000 tcp:3000
cd ../Voxa
.\gradlew.bat :app:assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk`. On the phone, use Profile → My plan → Activate a code, and enter the web exchange code. Confirm the caregiver PIN (or create one). Alternatively scan the QR in the app or open the custom activation link. Plan status is shown in Profile and the sidebar.

The exchange needs a connection once. The issued token is verified and stored locally; airplane mode does not prevent paid use through its signed period and grace. A paired token can also be pasted or supplied with `voxa://activate?token=...`; the token must name that exact installation. Never put a signing private key in an APK.

For a different development API host, supply `-PVOXA_WEB_URL=https://your-development-host`. Cleartext is accepted only for debug loopback/emulator aliases. HTTPS production App Links require matching Digital Asset Links and signing-certificate fingerprints.

## Report demonstration

An activated Family or Clinical profile can open Profile → Progress review, select 7 or 30 days, and export a one-page bilingual PDF or full version-2 JSON after caregiver PIN confirmation. The phone reads the selected child's real local activity; the website preview and downloadable examples use fictional data. Exports from one preview share its snapshot/report ID and use a narrowly scoped FileProvider directory.

In the web account, use “Upload report data.” `config/progress-report-example.json` is generated by the actual Android exporter from fictional observations; the public center preview consumes its identical copy in `lib/report-example.json`. Uploads are authenticated, ownership-scoped, deduplicated by account/report ID, and sorted newest first. The shared view shows two activity summaries and at most three short insights, with supporting numbers under Details. Center visitors can download the corresponding one-page English/Arabic PDF from `public/demo-reports/`. The highlight tests compare web output with the actual native exporter in both languages and verify that changing report counts changes the displayed text.

The strict version-2 allowlist validates denominators, daily/target totals, calendar boundaries/timezone, task cohorts, help totals, and review timing. Names, recordings, embeddings, transcripts, personal target phrases, and task notes are rejected. Version-1 reports remain readable with their limitations explicit. Old scores do not enter new comparisons; new task events support defined assignment cohorts and caregiver-reported assistance. See [report design](../REPORT_DESIGN.md) for formulas, privacy and engineering thresholds; these are app observations rather than clinical assessments.

## Production setup

Use a separate deployment/environment and set `VOXA_DEMO_MODE=false`. Do not copy demo keys into a release.

1. Create a PostgreSQL/Supabase project. Set a backend-only, TLS-enabled `DATABASE_URL`; run `npm run db:migrate` with the production environment loaded. The migration enables RLS without anonymous read/write policies. The browser uses Supabase Auth only; it never receives the database credential or a service-role key.
2. Configure Supabase email sign-in and permitted redirect URLs. Set `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_ANON_KEY`. Real users use a magic link before checkout. The preview does not provision real user accounts by itself.
3. Set the owned HTTPS `APP_URL`, a random `VOXA_SESSION_SECRET`, a production Ed25519 PEM private key in `VOXA_LICENSE_PRIVATE_KEY` (literal `\n` supported), and `VOXA_LICENSE_KEY_ID`. Keep the private key in the host secret store.
4. Review prices in `lib/catalog.ts`; Family prices follow business plan v2 (349 EGP monthly / 2,799 annually; 99 SAR monthly / 899 annually), and clinic plans are monthly only (2,999 EGP / 1,799 SAR). Configure matching Stripe recurring Price IDs for supported tier/currency/interval combinations. The server retrieves each Price and checks the catalog amount, currency, and recurrence before creating checkout. Unsupported configurations fail closed.
5. Configure the Stripe endpoint `/api/webhooks/stripe` for checkout completion/async success, subscription updates/deletion, invoice paid/payment failed, and charge refunded. Set `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET`; configure the Stripe customer portal. Only verified paid checkout grants access. Renewal events retrieve current provider state and do not extend access for an unpaid invoice.
6. For Paymob EGP, configure `PAYMOB_SECRET_KEY`, `PAYMOB_PUBLIC_KEY`, `PAYMOB_HMAC_SECRET`, and comma-separated `PAYMOB_INTEGRATION_IDS`. Use `/api/webhooks/paymob` as the processed callback. Wallets, cards, and other methods depend on those merchant integrations; the app does not claim unsupported methods. In this implementation these plans are prepaid, not auto-recurring.
7. Optionally configure `RESEND_API_KEY` and a verified `VOXA_FROM_EMAIL`. Successful live fulfillment emails the authenticated checkout address with an account link. It does not email reusable activation tokens or voice information. Delivery uses persistent deduplication plus the provider's idempotency key; failed configured delivery makes the payment webhook retry. No live email is sent in demo mode.
8. Deploy `web/` as the Next.js root. The public activation preview is already deployed to `jazz16/voxa-web`; customer accounts and real commerce need the production configuration above. Use durable PostgreSQL for those flows, never the local JSON demo store. Publishing this Git branch does not automatically redeploy the site.

## Production Android release

The release build rejects demo tokens by default and uses the reserved `voxa.invalid` host until an owned server is configured. Supply the production key ID and raw 32-byte Ed25519 public key (base64), never the PEM private key:

```powershell
.\gradlew.bat :app:bundleRelease `
  -PVOXA_WEB_URL=https://your-owned-domain.example `
  -PVOXA_LICENSE_KEY_ID=production-v1 `
  -PVOXA_LICENSE_PUBLIC_KEY=YOUR_RAW_BASE64_PUBLIC_KEY
```

Set `VOXA_KEYSTORE_PATH`, `VOXA_KEYSTORE_PASSWORD`, `VOXA_KEY_ALIAS`, and `VOXA_KEY_PASSWORD` as environment variables outside Git to sign. The APK/AAB generated without these is unsigned and is not a distributable production release.

For verified HTTPS App Links, set `VOXA_ANDROID_CERT_SHA256` and optionally `VOXA_ANDROID_APPLICATION_ID`, then run `node scripts/assetlinks.mjs` in `web/`. Deploy the resulting `public/.well-known/assetlinks.json` on the configured HTTPS host. Use the Play app-signing certificate fingerprint for a Play-distributed build.

External upgrade links are disabled in release by default. `-PVOXA_WEB_UPGRADE_ENABLED=true` is available for a distribution where that purchase flow is permitted. This is not a Google Play Billing implementation.

No future database update should erase trained profiles. Explicit migrations cover versions 8 → 9 → 10, preserving existing data and adding structured observations. Versions below 8 need explicit, verified migrations before distribution. On-device speech recognition requires Android 12+ and supported installed language services; other devices still retain core local intent recognition and emergency tools.

## Verification

```powershell
npm run typecheck
npm test
npm run build
# With the local server running:
node scripts/smoke.mjs

cd ../Voxa
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:bundleRelease
# On a disposable QA emulator only (the test initializes its license preferences):
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.voxa.LicenseDeviceTest'
```

The smoke test makes synthetic local demo accounts/orders and verifies signatures without printing activation credentials. Unit tests cover tampering, grace boundaries, ownership, device limits, idempotency, unpaid renewals, Paymob's documented HMAC sequence, and the report allowlist. The device test exercises Android Keystore, offline cached access, clock rollback, and an untrusted deep link requiring caregiver confirmation.

The hosted preview, reusable code generation, and device-bound license exchange have been verified. Layout checks covered English and Arabic pricing/activation, the landing hero, center benefits, FAQ dialogs, report week selection, and code copying. Live merchant callbacks, production database behavior, cross-device Nearby transfer, scanner availability on other phones, recognition languages, and clinical validity still require their own environment/device validation. Additional voice assets, biomarker analysis, multi-child batch handover, center invitations/calendar/dues, and automatic weekly report delivery are not represented as finished features. See [`IMPLEMENTATION_STATUS.md`](../IMPLEMENTATION_STATUS.md) for the scope audit.
