import { z } from "zod";
const count = z.number().int().nonnegative().max(10_000_000);
export const historyCaveat =
  "Older app versions recorded successful attempts only. Scores are not clinical measurements. Task counts are current status, not weekly completion history.";
export const reportSchema = z
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
    tasks: z
      .object({
        total: count,
        pending: count.optional(),
        submitted: count.optional(),
        unable: count.optional(),
        approved: count.optional(),
        rejected: count.optional(),
      })
      .strict(),
  })
  .strict()
  .superRefine((report, context) => {
    const daily = report.practice.daily;
    if (
      new Set(daily.map((d) => d.date)).size !== daily.length ||
      daily.length !== report.practice.activeDays ||
      daily.reduce((sum, d) => sum + d.recordedAttempts, 0) !==
        report.practice.recordedAttempts
    )
      context.addIssue({
        code: "custom",
        message: "Daily counts do not agree with the summary",
        path: ["practice"],
      });
    if (
      Object.entries(report.tasks)
        .filter(([key]) => key !== "total")
        .reduce((sum, [, value]) => sum + (value ?? 0), 0) !==
      report.tasks.total
    )
      context.addIssue({
        code: "custom",
        message: "Task counts do not agree with the total",
        path: ["tasks"],
      });
  });
export type ProgressSummary = z.infer<typeof reportSchema>;
