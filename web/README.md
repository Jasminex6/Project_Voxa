# Voxa web and activation bridge

This Next.js application includes a bilingual storefront, account/license dashboard, report templates, Stripe and Paymob adapters, and an explicit local demo. It is kept in the same branch as the Android licensing implementation.

## Hosted storefront

Live URL: https://voxa-web-pi.vercel.app/en (Arabic: `/ar`). Vercel project: `jazz16/voxa-web`. The public deployment has demo mode disabled; checkout and online account activation remain unavailable until production services below are configured. Simulated purchases and device activation continue to work in the local demo.

Deployments are manual from this `web` folder with `vercel deploy --prod`. `.vercelignore` excludes local keys, environment files, and demo data. The ignored `.vercel/` folder links this checkout to the project. The public pages and configuration were checked without Vercel authentication.

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

Install `app/build/outputs/apk/debug/app-debug.apk`. On the phone, use Profile → Subscription & license → Activate or renew, and paste the web exchange code. Confirm the caregiver PIN (or create one). Alternatively scan the QR in the app or open the custom activation link.

The exchange needs a connection once. The issued token is verified and stored locally; airplane mode does not prevent paid use through its signed period and grace. A paired token can also be pasted or supplied with `voxa://activate?token=...`; the token must name that exact installation. Never put a signing private key in an APK.

For a different development API host, supply `-PVOXA_WEB_URL=https://your-development-host`. Cleartext is accepted only for debug loopback/emulator aliases. HTTPS production App Links require matching Digital Asset Links and signing-certificate fingerprints.

## Report demonstration

An activated Family or Clinical profile can export summary JSON or PDF from Profile → Practice progress, after caregiver PIN confirmation. Files use distinct report IDs and a narrowly scoped FileProvider directory.

In the web account, use “Upload report JSON.” `config/progress-report-example.json` is a synthetic example for the demo. Uploads are authenticated, ownership-scoped, deduplicated by account/report ID, and sorted newest first. Reports exclude names, voice samples, acoustic templates, contacts, unrestricted text, and task notes. They do not claim clinical accuracy. Older app history includes successful attempts only, and task counts describe current statuses rather than weekly completion.

## Production setup

Use a separate deployment/environment and set `VOXA_DEMO_MODE=false`. Do not copy demo keys into a release.

1. Create a PostgreSQL/Supabase project. Set a backend-only, TLS-enabled `DATABASE_URL`; run `npm run db:migrate` with the production environment loaded. The migration enables RLS without anonymous read/write policies. The browser uses Supabase Auth only; it never receives the database credential or a service-role key.
2. Configure Supabase email sign-in and permitted redirect URLs. Set `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_ANON_KEY`. Real users use a magic link before checkout. The preview does not provision real user accounts by itself.
3. Set the owned HTTPS `APP_URL`, a random `VOXA_SESSION_SECRET`, a production Ed25519 PEM private key in `VOXA_LICENSE_PRIVATE_KEY` (literal `\n` supported), and `VOXA_LICENSE_KEY_ID`. Keep the private key in the host secret store.
4. Review prices in `lib/catalog.ts`; Family prices follow business plan v2 (349 EGP monthly / 2,799 annually; 99 SAR monthly / 899 annually), and clinic plans are monthly only (2,999 EGP / 1,799 SAR). Configure matching Stripe recurring Price IDs for supported tier/currency/interval combinations. The server retrieves each Price and checks the catalog amount, currency, and recurrence before creating checkout. Unsupported configurations fail closed.
5. Configure the Stripe endpoint `/api/webhooks/stripe` for checkout completion/async success, subscription updates/deletion, invoice paid/payment failed, and charge refunded. Set `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET`; configure the Stripe customer portal. Only verified paid checkout grants access. Renewal events retrieve current provider state and do not extend access for an unpaid invoice.
6. For Paymob EGP, configure `PAYMOB_SECRET_KEY`, `PAYMOB_PUBLIC_KEY`, `PAYMOB_HMAC_SECRET`, and comma-separated `PAYMOB_INTEGRATION_IDS`. Use `/api/webhooks/paymob` as the processed callback. Wallets, cards, and other methods depend on those merchant integrations; the app does not claim unsupported methods. In this implementation these plans are prepaid, not auto-recurring.
7. Optionally configure `RESEND_API_KEY` and a verified `VOXA_FROM_EMAIL`. Successful live fulfillment emails the authenticated checkout address with an account link. It does not email reusable activation tokens or voice information. Delivery uses persistent deduplication plus the provider's idempotency key; failed configured delivery makes the payment webhook retry. No live email is sent in demo mode.
8. Deploy `web/` as the Next.js root on the chosen Vercel project. No Vercel account/project or deployment token was supplied here; no public deployment was performed. Use durable PostgreSQL, never the local JSON demo store, on a hosted deployment.

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

No future database update should erase trained profiles. Version 8 upgrades use a tested migration to version 9; version 9 installations remain supported. Versions below 8 need explicit, verified migrations before distribution. On-device speech recognition requires Android 12+ and supported installed language services; other devices still retain core local intent recognition and emergency tools.

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

Live merchant callbacks, production database behavior, cross-device Nearby transfer, scanner module availability, recognition languages, clinical validity, and public deployment still require real environment/device validation. Additional voice assets, biomarker analysis, multi-child batch handover, center invitations/calendar/dues, and automatic weekly report delivery are not represented as finished features. See the repository's `IMPLEMENTATION_STATUS.md` for the scope audit.
