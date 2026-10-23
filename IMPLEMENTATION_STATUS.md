# Voxa platform implementation

Branch: `codex/voxa-platform-integration`, based on `justinbeiber` at `899ebbed53f9ca94e047835ca402a4a798964840`.

The supplied `EXECUTION_PLAN.md` is preserved. This file records actual implementation, deliberate corrections, validation, and work that is not complete. This is a working hackathon integration, not a claim that the entire commercial launch is finished.

## Implemented

- Android Free / Family Pro / Clinical SLP entitlement state; signed Ed25519 JWS validation with issuer, audience, key ID, installation binding, issue/expiry/grace dates, and strict claim checks.
- Android Keystore AES-GCM storage, local clock rollback detection, and periodic local expiry checks. Free allows one newly created profile and three word enrollments. Existing profiles, existing translations, emergency tools, and local data remain accessible after expiry.
- Entitlement checks at creation/import/enrollment/send actions, not only on buttons. Family enables the full practice library and sending nearby profiles; Clinical adds profile search. Receiving remains possible within the profile limit.
- Caregiver-confirmed activation via custom deep link, short-lived code, QR scanner, or a previously paired signed token. The scanner has no app camera permission but may need an initial Google Play Services module download.
- Numerical progress JSON/PDF export protected by caregiver PIN. No audio, embeddings, acoustic templates, names, contacts, task descriptions, or parent notes appear in the report contract.
- On-device-only speech practice. Unsupported devices/languages receive an explanation rather than a silent cloud fallback. Installed offline TTS voices are required when a prerecorded clip is unavailable.
- Microphone/file handling guards, bounded enrollment buffers, non-destructive database-upgrade policy, backup exclusions, release shrinking, and signing configuration through environment variables.
- Bilingual Next.js storefront, monthly/annual EGP/SAR Family plan selection using business plan v2 prices, account sign-in, subscription/device management, QR/deep-link fulfillment, report upload/view/print, privacy and terms drafts. The audio demo was removed; Support links to the supplied demo address voxasupport@gmail.com. Clinic subscriptions are monthly only.
- Profile contains the purchase activation-code field and plan status. The dashboard sidebar shows the current plan and links to Profile; practice and enrollment screens do not contain subscription cards or plan banners.
- My plan uses a compact accented summary with a status badge and expandable activation controls. Its matching sidebar shortcut is compact. The revised debug APK was built and installed on the connected Samsung.
- Hosted `/en/plan-demo` and `/ar/plan-demo` generate short Family Pro or Clinical SLP demo codes and QR codes from a pool of 100 reusable codes (50 per plan), with no device-ID entry. The app pairs automatically during redemption. Each token grants one installation seven days plus offline grace, without payment, account creation, or server-side child records. A separate server-only signer is trusted by debug builds only; paid licensing remains separate.
- The storefront explains child, family, and center benefits. For centers provides a concrete clinic/home workflow, an interactive synthetic weekly report, a clinic activation CTA, and the supplied support contact. Reports are explicitly examples; automated delivery and a real secretary dashboard are not claimed.
- Supabase Auth integration and PostgreSQL schema with RLS enabled and no public table policies. Server routes verify ownership.
- Stripe hosted subscription checkout, provider signature validation, paid-state verification, amount/currency/provider/order checks, duplicate fulfillment handling, renewal/cancellation reconciliation, full-refund revocation, and billing portal.
- Paymob Intention/Unified Checkout and HMAC-verified transaction callbacks. Paymob is explicitly prepaid/manual renewal in this version. Actual available methods are determined by merchant integrations.
- Optional transactional fulfillment email through Resend, with server-verified checkout email and delivery deduplication. Email links to the authenticated account, where a fresh activation code can be created. Demo mode never sends email.
- Explicit local-only demo checkout with persistent ignored demo data, real cryptographic signing, and no real charge. Demo routes and storage refuse common hosted deployment environments.
- CI workflow for web checks and Android builds.

## Corrections to the supplied plan

1. Signatures authenticate licenses; they are not encryption. Tokens contain no child practice data.
2. A transferable signed JWT cannot enforce a device limit by itself. QR codes therefore carry random, 15-minute exchange codes. The server pairs the installation and returns a device-bound token. A repeat on the same installation is safe; another installation cannot reuse the code. License references are not bearer credentials.
3. Offline access ends at the signed paid-through date plus seven days of grace. Refunds and revocations cannot instantly reach a disconnected device. Clock rollback checks reduce casual abuse but are not tamper-proof against a rooted/modified device.
4. No entitlement failure disables emergency actions or deletes trained templates. SMS, location, WhatsApp, and similar external services still have their own permission/carrier/connectivity requirements.
5. The actual branch used Android's general speech recognizer and text matching, rather than the practice-waveform scoring described in the plan. The implementation prevents online fallback and labels the score accurately.
6. Clinical outcome, biomarker, competitor-failure, compliance, and perfect-Lighthouse claims have not been invented for the website. Legal pages are clearly drafts.
7. External purchase navigation is enabled in debug and disabled by default in release. Any production distribution needs the appropriate billing-policy decision before enabling it.

## Verified locally

- 37 Android JVM tests passed, including hosted-plan token interoperability and the existing license-verification/entitlement tests.
- A targeted Samsung test decoded actual short-URL QR image fixtures, redeemed their codes on the live site, verified both tiers and their premium features, and rejected a different installation. It did not change the family's PIN, license, or data. Camera framing remains a user-operated scan; the app scanner now targets QR codes with auto-zoom and validates payloads before confirmation.
- A Pixel 6 Android 14 emulator test passed Keystore storage, airplane-mode license verification, invalid-token rejection, clock rollback handling, and caregiver confirmation for external activation links.
- Debug APK, minified unsigned release APK, and release AAB built; release vital lint passed. Artifacts require production signing/configuration before distribution.
- 24 web/backend tests passed, including public-demo tier/device binding, tampering, input allowlisting and explicit enablement, plus the existing commerce/privacy checks.
- TypeScript and Next.js production build passed.
- Local HTTP smoke test passed pending-order protection, price tampering, duplicate completion, device pairing, account isolation, cancellation, report deduplication/privacy, and disabled live webhooks.
- Browser verification covered English pricing, simulated purchase, verified order, QR/deep-link generation, Arabic navigation with the order query retained, and Arabic account layout.
- Primary website layouts were checked at 1280×720: pricing cards, center benefits, and activation controls/QR fit with the compact footer visible. English and Arabic pricing/activation were checked; FAQs and the interactive sample report open in native dialogs. Copy code, annual pricing, report week selection, and live Family Pro QR generation were verified after the layout changes. The landing hero fits before the scrolling How it works content. Narrow screens retain natural scrolling rather than clipping content.

## Not complete / launch requirements

- The site is deployed at https://voxa-web-pi.vercel.app in Vercel project `jazz16/voxa-web`. Deployment `dpl_DGnsLKSc5sxNFMhxrACAYeTSKPY6` reached READY with compact primary pages, activation actions beside the QR, the no-device-ID short-code flow, enlarged QR option, and 100-code CSV download. The live pool contains exactly 50 Family and 50 Clinical codes. This is a manual deployment, without automatic Git publishing.
- Website production build passes; Vercel upload rules exclude environment files and local demo data. Public demonstration activation is enabled with its own signer; the localhost JSON-store demo remains restricted to localhost. No live payments, Supabase/PostgreSQL, email, production licensing signer, or owned-domain credentials are configured. Real purchases/customer accounts remain separate launch work.
- Production legal/privacy review, a support contact, retention/deletion procedures, store distribution/billing decisions, and final prices are still required.
- Extra high-fidelity/dialect voice packs, validated biomarkers, custom Clinical practice content, and multi-child batch handover are not implemented. The existing individual handover flow is retained and gated; no extra voice assets or clinical validation data were supplied.
- The earlier center-seat invitation model, secretary appointments/dues/calendar, and automated weekly Android-to-center report delivery are not part of the supplied three-pillar execution implementation. Current reports are generated locally and uploaded deliberately through the authenticated web account. No doctor/center authorization model has been fabricated.
- Database versions 8 and 9 are preserved. The explicit 8-to-9 migration retains local enrollments and adds the imported-intent flag with a false default. Its preservation test passed on the connected Samsung, and the upgraded app launched successfully. Versions below 8 still require explicit migrations; destructive fallback is not allowed.
- Live PostgreSQL integration and multi-instance load testing remain unverified. The current small-pilot store serializes entitlement mutations with a database advisory lock. Production needs appropriate monitoring and a shared abuse/rate-limiting layer.

See `web/README.md` for local demo, Android activation, production configuration, and exact verification commands.
