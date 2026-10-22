import test from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { historyCaveat, reportSchema } from "../lib/report";
const report = () => ({
  schemaVersion: 1,
  reportId: randomUUID(),
  subjectId: randomUUID(),
  generatedAt: "2026-10-22T10:00:00Z",
  periodStart: "2026-10-21T10:00:00Z",
  periodEnd: "2026-10-22T10:00:00Z",
  measure: "speech_recognition_text_match",
  historyCaveat,
  practice: {
    recordedAttempts: 4,
    activeDays: 2,
    meanMatchScore: 75,
    daily: [
      { date: "2026-10-21", recordedAttempts: 2, meanMatchScore: 65 },
      { date: "2026-10-22", recordedAttempts: 2, meanMatchScore: 85 },
    ],
  },
  tasks: { total: 3, approved: 2, pending: 1 },
});
test("accepts the Android numerical-summary contract", () => {
  assert.equal(reportSchema.safeParse(report()).success, true);
});
test("rejects recordings, embeddings, names, free-text notes, and arbitrary caveats", () => {
  for (const field of [
    "recordings",
    "speakerEmbedding",
    "childName",
    "parentNote",
    "templates",
  ])
    assert.equal(
      reportSchema.safeParse({ ...report(), [field]: "private" }).success,
      false,
    );
  assert.equal(
    reportSchema.safeParse({ ...report(), historyCaveat: "private child note" })
      .success,
    false,
  );
});
test("rejects contradictory daily totals and task counts", () => {
  const value = report();
  value.practice.recordedAttempts = 999;
  assert.equal(reportSchema.safeParse(value).success, false);
  const other = report();
  other.tasks.total = 99;
  assert.equal(reportSchema.safeParse(other).success, false);
});
