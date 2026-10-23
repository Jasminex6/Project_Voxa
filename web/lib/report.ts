import { z } from "zod";
const count = z.number().int().nonnegative().max(10_000_000);
export const historyCaveat =
  "Older app versions recorded successful attempts only. Scores are not clinical measurements. Task counts are current status, not weekly completion history.";
const snapshot = z
  .object({
    total: count,
    pending: count,
    submitted: count,
    unable: count,
    approved: count,
    rejected: count,
  })
  .strict();
export const legacyReportSchema = z
  .object({
    schemaVersion: z.literal(1),
    reportId: z.string().uuid(),
    subjectId: z.string().uuid(),
    generatedAt: z.iso.datetime(),
    periodStart: z.iso.datetime().nullable(),
    periodEnd: z.iso.datetime().nullable(),
    measure: z.literal("speech_recognition_text_match"),
    historyCaveat: z.literal(historyCaveat),
    practice: z
      .object({
        recordedAttempts: count,
        activeDays: count,
        meanMatchScore: z.number().min(0).max(100).nullable(),
        daily: z
          .array(
            z
              .object({
                date: z.iso.date(),
                recordedAttempts: count,
                meanMatchScore: z.number().min(0).max(100),
              })
              .strict(),
          )
          .max(3660),
      })
      .strict(),
    tasks: snapshot.partial().required({ total: true }),
  })
  .strict()
  .superRefine((r, c) => {
    if (
      new Set(r.practice.daily.map((d) => d.date)).size !==
        r.practice.daily.length ||
      r.practice.daily.length !== r.practice.activeDays ||
      r.practice.daily.reduce((n, d) => n + d.recordedAttempts, 0) !==
        r.practice.recordedAttempts
    )
      c.addIssue({
        code: "custom",
        message: "Daily counts do not agree with the summary",
        path: ["practice"],
      });
    if (
      Object.entries(r.tasks)
        .filter(([k]) => k !== "total")
        .reduce((n, [, v]) => n + (v ?? 0), 0) !== r.tasks.total
    )
      c.addIssue({
        code: "custom",
        message: "Task counts do not agree with the total",
        path: ["tasks"],
      });
  });
const counts = z
  .object({
    scored: count,
    matched: count,
    unrecognized: count,
    noSpeech: count,
    technical: count,
    activeDays: count,
  })
  .strict()
  .superRefine((r, c) => {
    if (
      r.matched > r.scored ||
      r.activeDays > r.scored + r.unrecognized ||
      (r.scored + r.unrecognized > 0 && r.activeDays === 0)
    )
      c.addIssue({
        code: "custom",
        message: "Contradictory practice outcomes",
      });
  });
const targetCode = z
  .string()
  .regex(
    /^(core_(water|milk|bread|help|mom|dad|food|bathroom|sleep)|personal_[1-9][0-9]{0,5}|other)$/,
  );
export const reportV2Schema = z
  .object({
    schemaVersion: z.literal(2),
    reportId: z.string().uuid(),
    subjectId: z.string().uuid(),
    generatedAt: z.iso.datetime(),
    measure: z.literal("voxa_app_match_v1"),
    period: z
      .object({
        days: z.union([z.literal(7), z.literal(30)]),
        start: z.iso.datetime(),
        end: z.iso.datetime(),
        previousStart: z.iso.datetime(),
        previousEnd: z.iso.datetime(),
        timeZone: z
          .string()
          .max(80)
          .refine((v) => {
            try {
              new Intl.DateTimeFormat("en", { timeZone: v });
              return true;
            } catch {
              return false;
            }
          }, "Unknown time zone"),
      })
      .strict(),
    captureStartedAt: z.iso.datetime(),
    legacyScoredAttempts: count,
    practice: z
      .object({
        current: counts,
        previous: counts,
        daily: z
          .array(z.object({ date: z.iso.date(), counts }).strict())
          .max(30),
        targets: z
          .array(
            z
              .object({
                code: targetCode,
                language: z.enum(["ar", "en", "und"]),
                current: counts,
                previous: counts,
              })
              .strict(),
          )
          .max(41),
        omittedTargets: count,
      })
      .strict(),
    tasks: z
      .object({
        assigned: count,
        cohortApproved: count,
        submitted: count,
        approved: count,
        unable: count,
        support: z
          .object({
            independent: count,
            prompted: count,
            assisted: count,
            notRecorded: count,
          })
          .strict(),
        awaitingReview: count,
        timedAwaiting: count,
        oldestAwaitingHours: z
          .number()
          .nonnegative()
          .max(10_000_000)
          .nullable(),
        reviewHoursMedian: z.number().nonnegative().max(10_000_000).nullable(),
        reviewSamples: count,
        snapshot,
      })
      .strict(),
  })
  .strict()
  .superRefine((r, c) => {
    const bad = (message: string, path: (string | number)[] = []) =>
      c.addIssue({ code: "custom", message, path });
    const p = r.period,
      practice = r.practice,
      tasks = r.tasks;
    if (
      [p.start, p.end, p.previousStart, p.previousEnd].some(
        (v) => !Number.isFinite(Date.parse(v)),
      )
    )
      return;
    try {
      new Intl.DateTimeFormat("en", { timeZone: p.timeZone });
    } catch {
      return;
    }
    if (
      Date.parse(p.start) > Date.parse(p.end) ||
      Date.parse(p.previousStart) > Date.parse(p.previousEnd) ||
      Date.parse(p.previousEnd) >= Date.parse(p.start) ||
      Date.parse(p.end) !== Date.parse(r.generatedAt) ||
      Date.parse(r.captureStartedAt) > Date.parse(r.generatedAt)
    )
      bad("Invalid reporting period", ["period"]);
    const local = (iso: string) =>
      Object.fromEntries(
        new Intl.DateTimeFormat("en-CA", {
          timeZone: p.timeZone,
          year: "numeric",
          month: "2-digit",
          day: "2-digit",
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
          hourCycle: "h23",
        })
          .formatToParts(new Date(iso))
          .map((x) => [x.type, x.value]),
      );
    const localDate = (iso: string) => {
      const v = local(iso);
      return `${v.year}-${v.month}-${v.day}`;
    };
    const shift = (date: string, days: number) =>
      new Date(Date.parse(date) + days * 86400000).toISOString().slice(0, 10);
    const e = local(p.end),
      priorEnd = local(p.previousEnd);
    const wallTime = (parts: Record<string, string>) =>
      Date.UTC(
        +parts.year,
        +parts.month - 1,
        +parts.day,
        +parts.hour,
        +parts.minute,
        +parts.second,
      );
    const expectedPrior = {
      ...e,
      ...Object.fromEntries(
        ["year", "month", "day"].map((k) => [k, priorEnd[k]]),
      ),
    };
    const gap = wallTime(priorEnd) - wallTime(expectedPrior);
    // java.time shifts a nonexistent wall time forward through a DST gap. Cairo can also start a day at 01:00.
    const sameCutoff =
      gap === 0 ||
      (gap > 0 &&
        gap <= 10800000 &&
        wallTime(
          local(new Date(Date.parse(p.previousEnd) - gap).toISOString()),
        ) ===
          wallTime(expectedPrior) - gap);
    if (
      [p.start, p.previousStart].some(
        (v) =>
          localDate(new Date(Date.parse(v) - 1).toISOString()) === localDate(v),
      ) ||
      localDate(p.previousStart) !== shift(localDate(p.start), -p.days) ||
      localDate(p.previousEnd) !== shift(localDate(p.end), -p.days) ||
      !sameCutoff ||
      practice.daily[0]?.date !== localDate(p.start) ||
      practice.daily.at(-1)?.date !== localDate(p.end)
    )
      bad("Period boundaries must match local calendar days and cutoff", [
        "period",
      ]);
    if (
      practice.daily.length !== p.days ||
      new Set(practice.daily.map((d) => d.date)).size !== p.days
    )
      bad("Expected one row for each report day", ["practice", "daily"]);
    for (let i = 0; i < practice.daily.length; i++) {
      const day = practice.daily[i];
      if (
        day.counts.activeDays !==
        (day.counts.scored + day.counts.unrecognized > 0 ? 1 : 0)
      )
        bad("Invalid daily active-day count");
      if (
        i > 0 &&
        Date.parse(day.date) - Date.parse(practice.daily[i - 1].date) !==
          86400000
      )
        bad("Days must be consecutive and ordered");
    }
    for (const key of [
      "scored",
      "matched",
      "unrecognized",
      "noSpeech",
      "technical",
    ] as const) {
      if (
        practice.daily.reduce((n, d) => n + d.counts[key], 0) !==
        practice.current[key]
      )
        bad("Daily outcomes do not agree with totals", ["practice"]);
      for (const period of ["current", "previous"] as const)
        if (
          practice.targets.reduce((n, t) => n + t[period][key], 0) !==
          practice[period][key]
        )
          bad("Target outcomes do not agree with totals", [
            "practice",
            "targets",
          ]);
    }
    if (
      practice.daily.reduce((n, d) => n + d.counts.activeDays, 0) !==
        practice.current.activeDays ||
      practice.current.activeDays > p.days ||
      practice.previous.activeDays > p.days
    )
      bad("Invalid active-day total");
    if (
      new Set(practice.targets.map((t) => `${t.code}:${t.language}`)).size !==
        practice.targets.length ||
      practice.targets.some(
        (t) => (t.code === "other") !== (t.language === "und"),
      )
    )
      bad("Invalid or duplicate target grouping");
    if (
      practice.omittedTargets > 0 !==
      practice.targets.some((t) => t.code === "other")
    )
      bad("Missing target roll-up");
    if (
      practice.targets.some(
        (t) =>
          t.current.activeDays > practice.current.activeDays ||
          t.previous.activeDays > practice.previous.activeDays,
      )
    )
      bad("Target active days exceed the period total");
    if (tasks.cohortApproved > tasks.approved)
      bad("New-task confirmations exceed all confirmations");
    if (
      tasks.cohortApproved > tasks.assigned ||
      Object.values(tasks.support).reduce((a, b) => a + b, 0) !==
        tasks.approved ||
      tasks.timedAwaiting > tasks.awaitingReview ||
      tasks.awaitingReview !== tasks.snapshot.submitted ||
      tasks.reviewSamples > tasks.approved ||
      (tasks.timedAwaiting === 0) !== (tasks.oldestAwaitingHours === null) ||
      (tasks.reviewSamples === 0) !== (tasks.reviewHoursMedian === null)
    )
      bad("Contradictory task observations", ["tasks"]);
    if (
      Object.entries(tasks.snapshot)
        .filter(([k]) => k !== "total")
        .reduce((n, [, v]) => n + v, 0) !== tasks.snapshot.total
    )
      bad("Snapshot total does not agree with statuses");
  });
export const reportSchema = z.discriminatedUnion("schemaVersion", [
  legacyReportSchema,
  reportV2Schema,
]);
export type ProgressSummary = z.infer<typeof reportSchema>;
export type ProgressReview = z.infer<typeof reportV2Schema>;
export type ReportCounts = z.infer<typeof counts>;
export const usable = (counts: ReportCounts) =>
  counts.scored + counts.unrecognized;
export const matchRate = (counts: ReportCounts) =>
  usable(counts) ? (counts.matched * 100) / usable(counts) : null;
export function comparison(report: ProgressReview) {
  const eligible =
    Date.parse(report.captureStartedAt) >
    Date.parse(report.period.previousStart)
      ? []
      : report.practice.targets.filter(
          (t) =>
            t.code !== "other" &&
            usable(t.current) >= 5 &&
            usable(t.previous) >= 5 &&
            t.current.activeDays >= 2 &&
            t.previous.activeDays >= 2,
        );
  return {
    targets: eligible.length,
    currentTrials: eligible.reduce((n, t) => n + usable(t.current), 0),
    previousTrials: eligible.reduce((n, t) => n + usable(t.previous), 0),
    percentagePoints: eligible.length
      ? eligible.reduce(
          (n, t) => n + matchRate(t.current)! - matchRate(t.previous)!,
          0,
        ) / eligible.length
      : null,
  };
}
export function targetName(code: string, ar: boolean) {
  const core: Record<string, [string, string]> = {
    core_water: ["Water", "مياه"],
    core_milk: ["Milk", "لبن"],
    core_bread: ["Bread", "عيش"],
    core_help: ["Help", "مساعدة"],
    core_mom: ["Mom", "ماما"],
    core_dad: ["Dad", "بابا"],
    core_food: ["Food", "أكل"],
    core_bathroom: ["Bathroom", "حمام"],
    core_sleep: ["Sleep", "نوم"],
    other: ["Other targets", "أهداف أخرى"],
  };
  return (
    core[code]?.[ar ? 1 : 0] ??
    `${ar ? "هدف شخصي" : "Personal target"} ${code.replace("personal_", "")}`
  );
}

export function reviewHighlights(report: ProgressReview, ar: boolean) {
  const current = report.practice.current,
    tasks = report.tasks;
  const eligible =
    Date.parse(report.captureStartedAt) >
    Date.parse(report.period.previousStart)
      ? []
      : report.practice.targets.filter(
          (t) =>
            t.code !== "other" &&
            usable(t.current) >= 5 &&
            usable(t.previous) >= 5 &&
            t.current.activeDays >= 2 &&
            t.previous.activeDays >= 2,
        );
  const highlights: { title: string; detail: string }[] = [];
  const label = (t: ProgressReview["practice"]["targets"][number]) =>
    `${targetName(t.code, ar)} (${t.language === "ar" ? (ar ? "العربية" : "Arabic") : ar ? "الإنجليزية" : "English"})`;
  const change = (
    t: ProgressReview["practice"]["targets"][number],
    up: boolean,
  ) => ({
    title: up
      ? ar
        ? "مطابقات أكثر في التطبيق"
        : "More app matches"
      : ar
        ? "نقطة للنقاش في المتابعة"
        : "Worth discussing",
    detail: ar
      ? `${label(t)}: طابق التطبيق ${t.current.matched} من ${usable(t.current)}، مقابل ${t.previous.matched} من ${usable(t.previous)} سابقاً.`
      : `${label(t)}: ${t.current.matched} of ${usable(t.current)} matched, ${up ? "up" : "down"} from ${t.previous.matched} of ${usable(t.previous)}.`,
  });
  const captures = usable(current) + current.noSpeech + current.technical;
  if (captures > 0 && (current.noSpeech + current.technical) / captures >= 0.25)
    highlights.push({
      title: ar ? "راجع ظروف التسجيل" : "Check recording conditions",
      detail: ar
        ? `لم يُلتقط كلام أو حدث عطل في ${current.noSpeech + current.technical} من ${captures} تسجيلات. افحص الميكروفون وجرب مكاناً أهدأ.`
        : `${current.noSpeech + current.technical} of ${captures} captures had no speech or a technical issue. Check the microphone and try a quieter setting.`,
    });
  else {
    const sorted = [...eligible].sort(
      (a, b) =>
        matchRate(a.current)! -
        matchRate(a.previous)! -
        (matchRate(b.current)! - matchRate(b.previous)!),
    );
    const down = sorted[0],
      up = [...eligible].sort(
        (a, b) =>
          matchRate(b.current)! -
          matchRate(b.previous)! -
          (matchRate(a.current)! - matchRate(a.previous)!),
      )[0];
    if (down && matchRate(down.current)! < matchRate(down.previous)!)
      highlights.push(change(down, false));
    if (up && matchRate(up.current)! > matchRate(up.previous)!)
      highlights.push(change(up, true));
  }
  if (highlights.length === 0) {
    const focus = report.practice.targets
      .filter(
        (t) =>
          t.code !== "other" &&
          usable(t.current) >= 5 &&
          t.current.activeDays >= 2,
      )
      .sort((a, b) => matchRate(a.current)! - matchRate(b.current)!)[0];
    if (eligible.length)
      highlights.push({
        title: ar ? "استجابة متقاربة" : "Similar app responses",
        detail: ar
          ? "لم تتغير معدلات المطابقة للأهداف القابلة للمقارنة. استمر في متابعة الأهداف نفسها."
          : "Match rates are unchanged on comparable targets. Keep tracking the same words.",
      });
    else if (focus && matchRate(focus.current)! < 100)
      highlights.push({
        title: ar ? "هدف للمتابعة" : "A target to follow up",
        detail: ar
          ? `${label(focus)}: طابق التطبيق ${focus.current.matched} من ${usable(focus.current)}. لا توجد مقارنة سابقة كافية بعد.`
          : `${label(focus)}: ${focus.current.matched} of ${usable(focus.current)} matched. There isn't enough earlier data to compare yet.`,
      });
    else
      highlights.push({
        title: ar ? "جارٍ بناء صورة أولية" : "Building a starting picture",
        detail:
          usable(current) === 0
            ? ar
              ? "لم تُسجل ممارسة بالتتبع الجديد خلال هذه الفترة. ابدأ بجلسة قصيرة."
              : "No practice from the new tracking in this period. Start with a short session."
            : ar
              ? "كرر الكلمات واللغة نفسيهما حتى تتضح التغيرات في المراجعة المقبلة."
              : "Repeat the same words and language so a later review can show changes.",
      });
  }
  if (tasks.awaitingReview > 0)
    highlights.push({
      title: ar
        ? `مهام تنتظر تأكيدك: ${tasks.awaitingReview}`
        : tasks.awaitingReview === 1
          ? "1 task needs your review"
          : `${tasks.awaitingReview} tasks need your review`,
      detail: ar
        ? "أكد إتمام المهام وسجل المساعدة المطلوبة."
        : "Confirm completion and note any help needed.",
    });
  else {
    const known =
        tasks.support.independent +
        tasks.support.prompted +
        tasks.support.assisted,
      helped = tasks.support.prompted + tasks.support.assisted;
    if (known > 0)
      highlights.push({
        title: ar ? "المساعدة في المنزل" : "Support at home",
        detail: ar
          ? `حسب ولي الأمر، لزمت المساعدة في ${helped} من ${known} مهام سُجل فيها مستوى المساعدة.`
          : `The caregiver reported help on ${helped} of ${known} tasks with a recorded support level.`,
      });
  }
  return highlights.slice(0, 3);
}
