import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import example from "../lib/report-example.json";
import {
  comparison,
  matchRate,
  reportSchema,
  reportV2Schema,
  reviewHighlights,
} from "../lib/report";

const fresh = () => structuredClone(example);
test("short highlights match the real Android exporter in both languages", () => {
  const expected = JSON.parse(
    readFileSync(
      new URL(
        "../../config/progress-review-highlights-example.json",
        import.meta.url,
      ),
      "utf8",
    ),
  );
  const r = reportV2Schema.parse(fresh());
  assert.deepEqual(reviewHighlights(r, false), expected.en);
  assert.deepEqual(reviewHighlights(r, true), expected.ar);
  assert.equal(reviewHighlights(r, false).length, 3);
});
test("changing real report counts changes the insight instead of returning sample text", () => {
  const r = fresh();
  r.practice.targets.find((t) => t.code === "core_milk")!.current.matched--;
  r.practice.current.matched--;
  r.practice.daily.find((d) => d.date === "2026-10-19")!.counts.matched--;
  const summary = reviewHighlights(reportV2Schema.parse(r), false);
  assert.match(summary[0].detail, /Milk \(Arabic\): 5 of 12/);
});
test("the short view never claims a trend before sufficient earlier tracking", () => {
  const r = reportV2Schema.parse(fresh());
  r.captureStartedAt = r.period.start;
  const summary = reviewHighlights(r, false);
  assert.ok(
    summary.every(
      (h) => h.title !== "More app matches" && h.title !== "Worth discussing",
    ),
  );
  assert.match(summary[0].detail, /isn't enough earlier data/);
});
test("accepts Cairo midnight and cutoff gaps as resolved by Android java.time", () => {
  for (const period of [
    {
      start: "2026-04-23T22:00:00Z",
      end: "2026-04-30T12:00:00Z",
      previousStart: "2026-04-16T22:00:00Z",
      previousEnd: "2026-04-23T13:00:00Z",
      firstDay: "2026-04-24",
    },
    {
      start: "2026-04-24T21:00:00Z",
      end: "2026-04-30T21:30:00Z",
      previousStart: "2026-04-17T22:00:00Z",
      previousEnd: "2026-04-23T22:30:00Z",
      firstDay: "2026-04-25",
    },
  ]) {
    const r = fresh();
    Object.assign(r.period, period);
    delete (r.period as Record<string, unknown>).firstDay;
    r.generatedAt = period.end;
    r.captureStartedAt = "2026-04-01T00:00:00Z";
    r.practice.daily.forEach((d, i) => {
      d.date = new Date(Date.parse(period.firstDay) + i * 86400000)
        .toISOString()
        .slice(0, 10);
    });
    assert.equal(reportV2Schema.safeParse(r).success, true);
  }
});
test("native Android fixture is accepted unchanged and matches the public sample", () => {
  const r = reportV2Schema.parse(fresh());
  assert.deepEqual(
    JSON.parse(
      readFileSync(
        new URL("../../config/progress-report-example.json", import.meta.url),
        "utf8",
      ),
    ),
    example,
  );
  assert.equal(r.practice.current.matched, 30);
  assert.equal(matchRate(r.practice.current), 60);
  const c = comparison(r);
  assert.equal(c.targets, 3);
  assert.equal(c.currentTrials, 44);
  assert.ok(Math.abs(c.percentagePoints! - 8.3333333333333) < 0.0001);
  assert.equal(r.tasks.cohortApproved, 3);
  assert.equal(r.tasks.approved, 4);
});
test("comparison withholds sparse observations and incomplete tracking", () => {
  const r = reportV2Schema.parse(fresh());
  assert.equal(
    comparison({ ...r, captureStartedAt: r.period.start }).percentagePoints,
    null,
  );
  assert.equal(
    comparison({
      ...r,
      practice: {
        ...r.practice,
        targets: r.practice.targets.map((t) => ({
          ...t,
          current: { ...t.current, activeDays: 1 },
        })),
      },
    }).percentagePoints,
    null,
  );
});
test("report schema rejects private text at every exported level", () => {
  for (const key of [
    "childName",
    "recordings",
    "parentNote",
    "speakerEmbedding",
    "transcript",
  ]) {
    const r = fresh();
    assert.equal(
      reportSchema.safeParse({ ...r, [key]: "PRIVATE" }).success,
      false,
    );
    assert.equal(
      reportSchema.safeParse({
        ...r,
        practice: { ...r.practice, [key]: "PRIVATE" },
      }).success,
      false,
    );
    assert.equal(
      reportSchema.safeParse({ ...r, tasks: { ...r.tasks, [key]: "PRIVATE" } })
        .success,
      false,
    );
  }
  const r = fresh();
  r.practice.targets[0].code = "PRIVATE custom phrase";
  assert.equal(reportSchema.safeParse(r).success, false);
});
test("rejects inconsistent outcomes, task cohorts, timing and local period boundaries", () => {
  const mutations = [
    (r: ReturnType<typeof fresh>) => {
      r.practice.current.matched = 999;
    },
    (r: ReturnType<typeof fresh>) => {
      r.practice.daily[0].counts.technical++;
    },
    (r: ReturnType<typeof fresh>) => {
      r.practice.targets[0].previous.scored++;
    },
    (r: ReturnType<typeof fresh>) => {
      r.tasks.cohortApproved = r.tasks.assigned + 1;
    },
    (r: ReturnType<typeof fresh>) => {
      r.tasks.support.independent++;
    },
    (r: ReturnType<typeof fresh>) => {
      r.tasks.oldestAwaitingHours = null as unknown as number;
    },
    (r: ReturnType<typeof fresh>) => {
      r.period.timeZone = "not/a-zone";
    },
    (r: ReturnType<typeof fresh>) => {
      r.period.start = "invalid";
    },
    (r: ReturnType<typeof fresh>) => {
      r.practice.daily[0].date = "2026-10-16";
    },
    (r: ReturnType<typeof fresh>) => {
      r.period.previousEnd = "2026-10-16T09:00:00Z";
    },
  ];
  for (const mutate of mutations) {
    const r = fresh();
    mutate(r);
    assert.equal(reportSchema.safeParse(r).success, false);
  }
});
test("legacy uploaded summaries remain readable", () => {
  const legacy = JSON.parse(
    readFileSync(
      new URL(
        "../../config/progress-report-legacy-example.json",
        import.meta.url,
      ),
      "utf8",
    ),
  );
  assert.equal(reportSchema.parse(legacy).schemaVersion, 1);
});
