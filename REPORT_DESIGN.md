# Progress review: decisions and measurement contract

The report should help a caregiver and clinician decide what to discuss next. It describes practice activity and app responses, not a diagnosis, speech clarity, pronunciation accuracy, or treatment efficacy. No additional cloud processing or automatic upload is introduced.

## Questions the report answers

| Question | Evidence shown | How to use it |
|---|---|---|
| Is practice happening regularly? | Days with usable speech attempts / 7 or 30 calendar days, with a daily capture chart | Discuss gaps and feasible practice routines rather than reward a large lifetime total. |
| Is the app accepting repeated targets more often? | Matches / usable attempts per target and language; comparable-period change | Compare the same target and language; review difficult targets alongside the recognizer and environment. |
| Could capture conditions explain the results? | No-speech and technical captures, separately counted | Resolve recording/service conditions before interpreting non-matches. |
| Are new home tasks being followed through? | Newly assigned tasks confirmed / tasks assigned in this period | Use a defined assignment cohort; do not divide all approvals by current task cards. |
| How much help was needed? | Caregiver-reported independent, prompted, directly assisted, or not recorded | Discuss support explicitly. Tapping Done never implies independence. Recording help is optional and does not affect rewards. |
| Is caregiver review holding up the workflow? | Current submitted queue, oldest known wait, median submission-to-confirmation delay and sample size | Separate caregiver response from child activity; this is not child execution speed. |

## Denominators and comparison

Each recognizer operation records one structured outcome: scored response, no recognized match, no speech, or technical issue. Usable attempts = scored responses + unrecognized responses. Matches are scored responses accepted by the existing word/alias matcher. No-speech and technical captures are excluded from the match denominator. Active days require at least one usable attempt; an engine failure alone does not count as practice.

The selected period includes today up to export time and the preceding 6 or 29 local calendar days. The prior period uses the same local-time cutoff, shifted back 7 or 30 days in the device's IANA timezone. No later activity is included. Day boundaries remain local across daylight-saving changes.

A target/language pair is comparable only when each period has at least 5 usable attempts across at least 2 days, and structured tracking began before the prior period. Overall change is the **equal-weight mean of per-target rate changes in percentage points**, with eligible target and trial counts displayed. A change in the mix of practiced words cannot inflate this measure through weighting alone. These minimums are engineering safeguards against very sparse data, not clinically validated thresholds. Insufficient data produces “building a baseline,” never a manufactured improvement.

Examples: the fictional Android fixture has 30/50 app matches this period. Three comparable targets have changes +16.7, -16.7 and +25.0 percentage points, giving +8.3 points with equal weighting. The new personal target has no prior baseline and does not enter this comparison.

## Task evidence and retention

Assignments and real status transitions are written transactionally with the existing task state. Duplicate same-status approval creates neither evidence nor another reward. Task approval asks about support without making it mandatory. An assignment's latest observed state determines whether that period's new-task cohort is confirmed. Total period confirmations can include older assignments and are shown separately.

Numeric event history survives clearing completed task cards; the confirmation copy explains that distinction. Deleting a child profile cascades to its observation history. Old tasks retain their current status, but their unrecorded submission/approval times and support remain unknown. Queue timing reports its known fraction; the median includes only observed submission/confirmation pairs and shows its sample size.

## Privacy and compatibility

The local Room 9-to-10 migration adds observation tables and preserves profiles, enrollments, scores, tasks, and rewards. Existing 8-to-9 migration remains registered. Old scored entries are counted for coverage context but excluded from new rates because older versions sometimes recorded successes only. An upgraded phone needs new practice before a reliable baseline exists.

The version-2 export is a strict allowlist: pseudonymous subject/report IDs, reporting boundaries/timezone, outcome counts by day and target/language, numeric task cohorts/help/review timing, and a current task snapshot. It excludes names, recordings, acoustic templates, embeddings, recognized transcripts, contacts, task titles/descriptions, and private notes. Built-in targets use public codes. Personal phrases become stable per-profile numbered targets; the phrase-to-number mapping stays on the phone. At most 40 target/language groups are shown individually; remaining groups are aggregated without losing counts and excluded from comparison. A pseudonymous ID can link successive reports, so sharing remains caregiver-controlled.

Profile → Progress review offers a local review, 7/30-day selection, and PIN-protected PDF/JSON sharing. Exports from the same preview share its snapshot and report ID. A fresh review rebuilds the data. The PDF is a readable bilingual review with task context and optional target appendices; JSON is the interoperable companion. Both work offline. The website renders the same version-2 contract; older version-1 uploads remain readable with their limitations explicit. The public center preview and downloadable PDFs use fictional data generated by the actual Android exporter. No public upload endpoint or automatic center delivery is added.

## Design references and validation

[ASHA documentation guidance](https://www.asha.org/Practice-Portal/Professional-Issues/Documentation-in-Health-Care/) supports describing objective progress toward goals and relevant assistance/cueing. This informed the questions and support choices; it does not validate the speech recognizer or these app metrics.

Validation covers denominator exclusions, target-mix bias, language separation, sparse/partial baselines, legacy data, task cohorts, unknown review timing, zero-activity periods, and target roll-up. Device tests exercise migration preservation, durable task history, duplicate transitions, empty/large reports, bilingual PDF rendering, and themed review UI using synthetic data. Web tests consume the actual Android fixture, reject private fields and contradictory counts/periods, and preserve version-1 compatibility. All sample PDFs are visually inspected after rendering.
