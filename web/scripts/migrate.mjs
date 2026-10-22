import postgres from "postgres";
import { readFile } from "node:fs/promises";
if (!process.env.DATABASE_URL) throw new Error("DATABASE_URL is required");
const sql = postgres(process.env.DATABASE_URL, { max: 1, prepare: false });
try {
  await sql.unsafe(
    await readFile(new URL("../db/schema.sql", import.meta.url), "utf8"),
  );
  console.log("Commerce schema installed.");
} finally {
  await sql.end();
}
