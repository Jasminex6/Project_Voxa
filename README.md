# Voxa (فوكسا)

> Amplifying communication patterns the child already has.
>
> CU AI Nexus Hackathon 2026 · Healthcare & Wellbeing / Inclusive AI & Accessibility

Voxa is an Android assistive communication app for minimally verbal and non-speaking autistic children. Caregivers associate a child's familiar vocalizations with Arabic phrases, and Voxa recognizes those sounds locally to help the child communicate. Speech practice, caregiver-approved tasks, and rewards support daily use.

Core communication works offline. Plan activation needs a connection once; progress reports are generated on the phone and shared deliberately by the caregiver. Recordings and recognition templates are excluded from those reports.

## Live website

- [English storefront](https://voxa-web-pi.vercel.app/en) · [العربية](https://voxa-web-pi.vercel.app/ar)
- [Compare plans](https://voxa-web-pi.vercel.app/en/pricing)
- [Voxa for centers](https://voxa-web-pi.vercel.app/en/clinical)
- [Generate an activation code and QR](https://voxa-web-pi.vercel.app/en/plan-demo)

This branch, `codex/voxa-platform-integration`, builds on `justinbeiber`, integrates `dark/light_theme`, and includes the Android licensing integration and bilingual web platform. The hosted preview demonstrates plan activation without charging money or asking for payment details.

Plans, center benefits, and activation controls fit in one desktop view at the checked 1280×720 viewport. The activation form has a compact Generate button, with Copy code and Open in Voxa beside the QR. FAQs and the sample progress report open in dialogs. How it works remains a scrolling explanation; narrow screens retain normal scrolling so content stays readable.

## Try plan activation

1. Open the [activation page](https://voxa-web-pi.vercel.app/en/plan-demo), select Family Pro or Clinical SLP, and generate a code and QR.
2. In the updated Android **debug demo**, open **Profile → My plan → Activate a code**.
3. Scan the QR inside Voxa or enter the short `VOXA-F-...` / `VOXA-C-...` code. Confirm the caregiver PIN, or create one if prompted.
4. The app automatically supplies its installation ID and receives a signed, device-bound license. Plan access then works offline for seven days, followed by seven days of grace.

The website has **100 stable reusable demo codes: 50 per plan**. Download the complete CSV from the activation page. A code can be reused on other test phones; entering a device code on the website is unnecessary. These public hackathon codes demonstrate activation, rather than payment verification or purchased seats. Release APKs do not trust the demo signer.

## Features

| Area | Current behavior |
|---|---|
| Communication | Caregiver-enrolled vocalizations are matched locally and played back as Arabic phrases using bundled audio or Android TTS. |
| Enrollment and library | Record training examples, manage vocabulary per child, and replay recordings made on the local device. |
| Speech practice | Arabic/English word practice uses Android's on-device speech recognizer and text matching, with rewards and per-profile history. It requires Android 12+ and a supported installed language service; online recognition is not used as a fallback. |
| Quests and rewards | Caregivers create tasks, review submissions, award points, and fulfill rewards. Caregiver actions are PIN protected. |
| Profiles and handover | Create and switch child profiles, choose avatars and voices, and transfer profiles between trusted nearby devices. Sending a profile requires a paid tier. |
| My plan | Compact plan status and activation controls live in Profile, with a plan indicator in the sidebar. |
| Progress reports | Family and Clinical users can export summary JSON or PDF after caregiver confirmation. Authenticated web accounts support formatted report uploads, deduplication, and newest-first ordering. |
| Emergency tools | Contact dialer, SMS/WhatsApp launchers, location sharing, and a siren. External calls, messages, and location sharing depend on the relevant services, permissions, and connectivity. |
| Languages | Arabic and English interfaces, including right-to-left website layouts. |
| Appearance | Light/dark switch in the Home header, with the choice retained after restarting. Until a choice is made, the app follows the device theme. Profile, plan controls, reports, sharing sheets, and the sidebar use the same palette. |

### Nearby sharing and theme integration

The existing Nearby Connections transfer and receiver PIN flow are retained. Sharing from an individual profile card exports that selected child's metadata and recognition templates, even when another child is active. Receiver imports save the profile and vocabulary in one database transaction; an incomplete import rolls back. Raw recordings remain on the sender, so imported words can be recognized but their original recording previews are unavailable.

Share requires an active Family Pro or Clinical SLP entitlement. Receive checks the receiver's profile allowance before opening the transfer; a Free installation with an existing profile is already at its one-profile limit. Existing data is preserved. Runtime permission requests account for Android version differences.

To check two phones, install the merged build on both, activate a paid tier on the sender, choose Share on the intended profile, choose Receive on the other device, and enter the displayed pairing PIN. Switch themes and restart either app to verify the chosen appearance persists. A complete radio transfer requires two devices; the automated tests separately cover selected-profile serialization, recognition-template preservation, and failed-import rollback using synthetic in-memory databases.

## Plans

Catalog prices are defined in [`web/lib/catalog.ts`](web/lib/catalog.ts). The hosted demo charges nothing.

| Plan | EGP | SAR | Main access |
|---|---|---|---|
| Free | 0 | 0 | One child profile, three custom words, practice preview, and receiving a nearby handover. |
| Family Pro | 349/month or 2,799/year | 99/month or 899/year | Unlimited custom sounds, multiple profiles, full practice library, report export, sending/receiving handovers, and two activated devices. |
| Clinical SLP | 2,999/month | 1,799/month | Family features with licensing for up to 25 activated devices. No annual clinic price is configured. |

Expiry preserves existing profiles and trained sounds. Emergency tools and existing translations remain available. Paid access is verified locally through its signed expiry and grace period.

Microphone modes on Home and Practice: Hold records only while pressed and stops on release or cancellation; Always On / Continuous uses a tap to start and another tap to stop. The Practice recognizer can finish a word automatically. Its microphone hint follows the selected mode.

## Reports and privacy

Every report visibly includes User ID: the selected child's full anonymous profile identifier. It links successive reports without including their name; the report reference identifies the individual export.

Profile → Progress review shows a short overview: practice days, new tasks confirmed, and up to three plain-language insights. The PDF is one page; supporting numbers are behind Details and in the full JSON export. Phone reports use the selected child's real local activity. The website preview and sample downloads are explicitly fictional. PDF and JSON sharing require caregiver PIN confirmation and work offline. No-speech and technical captures never reduce the match rate, and insufficient data never produces a made-up trend.

The website's center report uses illustrative data. Actual reports can be uploaded through the configured web account flow; the public hosted activation demo does not provision customer accounts. Automatic weekly uploads, doctor access, center membership invitations, appointments, calendars, and payment-dues management are not implemented in this preview.

Comparisons use the same target and language with repeated observations in both periods and sufficient tracking coverage. Older scores are retained but excluded from these rates because earlier versions sometimes recorded successes only. New task assignments, confirmations, help levels, and caregiver review delays use durable observations; the current task snapshot is labeled separately. Clearing completed task cards retains these numeric observations; deleting the profile removes them.

Reports exclude names, recordings, embeddings, templates, transcripts, task text, contacts, and private notes. Personal targets use pseudonymous numbers. The center preview renders the same format using fictional data and offers bilingual sample PDFs. See [report design and formulas](REPORT_DESIGN.md) for definitions, sample thresholds, privacy, and limitations.

## Recognition architecture

The active Android communication engine uses **MFCC features and DTW template matching**:

```text
Microphone: 16 kHz mono PCM
  → Energy-based voice activity detection
  → Silence trimming, matching enrollment preprocessing
  → 40-dimensional MFCC features
  → DTW comparison with enrolled templates (top-two consensus)
  → Absolute-distance and ambiguity gates
  → Arabic phrase playback, with recognition suppressed until playback completes
  → 1.5-second echo cooldown; discard speaker audio before listening resumes
```

Enrollment stores multiple local feature templates for each intent. YAMNet and prototypical-matching code/assets remain in the repository from earlier experiments; they are not the active `VoxaClassifierEngine` pipeline. Speech practice uses the separate on-device speech-recognition flow described above.

Android stores profiles, vocabulary, practice history, tasks, and rewards in Room. Explicit 8 → 9 → 10 migrations preserve existing data; version 10 adds structured report observations. License verification and storage are handled on the device; signing private keys belong only on the server.

## Repository layout

```text
Voxa/                     Android app: Kotlin, Jetpack Compose, Room
  app/src/main/           App UI, recognition engine, licensing, report export
  app/src/test/           JVM tests
  app/src/androidTest/    Device and migration tests
web/                      Next.js website and backend routes
  components/             Storefront, activation, accounts, report views
  lib/                    Catalog, licenses, reports, commerce adapters
  db/                     PostgreSQL schema
  scripts/                Local setup, hosted demo, migrations, smoke checks
  tests/                  Web/backend tests
config/                   Public verification keys and synthetic test fixtures
models/                   Model assets from recognition experiments
notebooks/                Audio research and prototypes
IMPLEMENTATION_STATUS.md  Verification record and remaining launch work
```

## Run locally

Use Node.js 22.19+, JDK 21, an Android SDK matching the Gradle configuration (compile SDK 36.1), and a compatible Android Studio. The app supports Android 8.0+ for core features; on-device speech practice requires Android 12+ and an available language service. Use a physical phone for microphone, scanner, and speaker checks.

From the repository root:

```powershell
cd web
npm ci
npm run setup:demo
npm run dev
```

Open [localhost](http://127.0.0.1:3000/en). The setup creates ignored local demo keys and configuration plus public verification fixtures. The local simulated checkout is separate from the public hosted reusable-code demo.

To build an Android debug APK against the hosted activation demo, open another terminal at the repository root:

```powershell
cd Voxa
.\gradlew.bat :app:assembleDebug -PVOXA_WEB_URL=https://voxa-web-pi.vercel.app
```

Install `Voxa/app/build/outputs/apk/debug/app-debug.apk`, or open `Voxa/` in Android Studio and run the app. For localhost activation instead, build without the URL override and run `adb reverse tcp:3000 tcp:3000` on the connected device.

See [`web/README.md`](web/README.md) for hosted-demo configuration, report uploads, deployment, and production signing/payment setup. Never commit `.env` files, signing keys, local orders, or child data.

## Verification

From `web/`, after local demo setup:

```powershell
npm run typecheck
npm test
npm run build
# With the local demo server running:
node scripts/smoke.mjs
```

From `Voxa/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

The short-report revision passed 33 web/backend tests, 52 Android JVM tests, and five focused Samsung checks for bilingual overview UI, one-page exports with empty/large data, durable task events, and migration preservation. Earlier integration checks also covered onboarding language switching/recreation, both themes, selected-profile transfer/import rollback, and live activation. The 13 report/theme/transfer/migration/license checks also passed on the emulator. The local HTTP smoke test covers both report versions, privacy validation, ownership and deduplication. Both one-page bilingual sample PDFs were rendered and visually inspected. Two-phone Nearby radio transfer was not repeated. See [`IMPLEMENTATION_STATUS.md`](IMPLEMENTATION_STATUS.md) for the scope and limits of verification.

Onboarding language changes apply immediately by recreating the existing activity; setup fields and the current step survive recreation. The same locale helper serves Home's language setting. Current work is isolated on `codex/progress-review-onboarding`, branched from the merged platform integration.

GitHub Actions runs the web checks and Android build on pushes and pull requests. Device tests need a suitable emulator or connected phone; the license-state test must only run on a disposable QA device because it initializes license preferences.

## Deployment and remaining work

The website is deployed manually from `web/` to Vercel project `jazz16/voxa-web`; pushing this Git branch does not publish another website deployment. Stripe/Paymob adapters, account flows, and report storage code exist, but live merchant accounts, a production database/auth service, email delivery, and production licensing/signing are not configured for the hosted preview.

The contact address `voxasupport@gmail.com` is a demo placeholder. The center view is a service explanation and sample report, rather than a live secretary or doctor dashboard. Production distribution still needs configured services, release signing, explicit migrations for database versions below 8, and reviewed legal/privacy documents. Full details are in [`IMPLEMENTATION_STATUS.md`](IMPLEMENTATION_STATUS.md).

## Team

CU AI Nexus Hackathon 2026 — Cairo University · Healthcare & Wellbeing / Inclusive AI & Accessibility
