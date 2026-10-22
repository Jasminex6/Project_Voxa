import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import postgres from "postgres";
import type { Currency, Interval, PaidTier } from "./catalog";
import type { ProgressSummary } from "./report";

export type Order = {
  id: string;
  userId: string;
  tier: PaidTier;
  currency: Currency;
  interval: Interval;
  amount: number;
  gateway: "demo" | "stripe" | "paymob";
  status: "pending" | "paid" | "refunded";
  gatewayId: string | null;
  createdAt: number;
  billingEmail?: string | null;
};
export type Subscription = {
  id: string;
  userId: string;
  orderId: string;
  gateway: string;
  gatewaySubscriptionId: string | null;
  gatewayCustomerId: string | null;
  tier: PaidTier;
  status: "active" | "past_due" | "canceled" | "revoked";
  currentPeriodEnd: number;
  updatedAt: number;
};
export type License = {
  id: string;
  userId: string;
  subscriptionId: string;
  licenseKey: string;
  tier: PaidTier;
  maxDevices: number;
  isRevoked: boolean;
  createdAt: number;
};
export type Activation = {
  id: string;
  userId: string;
  licenseId: string;
  deviceId: string;
  activatedAt: number;
  lastPingAt: number;
};
export type Exchange = {
  id: string;
  userId: string;
  licenseId: string;
  codeHash: string;
  expiresAt: number;
  deviceId: string | null;
  redeemedAt: number | null;
};
export type Event = {
  id: string;
  userId: string;
  gateway: string;
  gatewayEventId: string;
  processedAt: number;
};
export type Report = {
  id: string;
  userId: string;
  subjectId: string;
  reportId: string;
  generatedAt: number;
  summary: ProgressSummary;
};
export type Tables = {
  orders: Order;
  subscriptions: Subscription;
  licenses: License;
  device_activations: Activation;
  activation_exchanges: Exchange;
  webhook_events: Event;
  reports: Report;
};
export type Table = keyof Tables;
export interface Store {
  all<K extends Table>(table: K): Promise<Tables[K][]>;
  put<K extends Table>(table: K, row: Tables[K]): Promise<void>;
}
type State = { [K in Table]: Tables[K][] };
const empty = (): State => ({
  orders: [],
  subscriptions: [],
  licenses: [],
  device_activations: [],
  activation_exchanges: [],
  webhook_events: [],
  reports: [],
});

export class MemoryStore implements Store {
  constructor(readonly state: State = empty()) {}
  async all<K extends Table>(table: K): Promise<Tables[K][]> {
    return this.state[table] as Tables[K][];
  }
  async put<K extends Table>(table: K, row: Tables[K]) {
    const rows = this.state[table] as Tables[K][];
    const index = rows.findIndex((r) => r.id === row.id);
    if (index < 0) rows.push(row);
    else rows[index] = row;
  }
}

let sqlClient: ReturnType<typeof postgres> | undefined;
let serial: Promise<unknown> = Promise.resolve();
export function demoMode(): boolean {
  return process.env.VOXA_DEMO_MODE === "true";
}

/** All issuance changes are atomic. Production transactions serialize entitlement mutations.
 * Keep this service-role connection server-only; RLS blocks direct anonymous table access.
 */
export async function transaction<T>(
  fn: (store: Store) => Promise<T>,
): Promise<T> {
  if (demoMode()) {
    if (process.env.VERCEL || process.env.CF_PAGES || process.env.RENDER)
      throw new Error("Demo storage is local-only");
    const task = serial.then(async () => {
      const path = resolve(process.cwd(), ".local/demo-store.json");
      let state: State;
      try {
        state = JSON.parse(await readFile(path, "utf8"));
      } catch (e) {
        if ((e as NodeJS.ErrnoException).code !== "ENOENT") throw e;
        state = empty();
      }
      state = { ...empty(), ...state }; // Add newly introduced demo tables without resetting orders.
      const store = new MemoryStore(state);
      const result = await fn(store);
      await mkdir(dirname(path), { recursive: true });
      await writeFile(path + ".tmp", JSON.stringify(state), { mode: 0o600 });
      await rename(path + ".tmp", path);
      return result;
    });
    serial = task.catch(() => undefined);
    return task;
  }
  if (!process.env.DATABASE_URL) throw new Error("Database is not configured");
  sqlClient ??= postgres(process.env.DATABASE_URL, {
    max: 3,
    prepare: false,
    transform: postgres.camel,
    types: { bigint: postgres.BigInt },
  });
  // Numeric timestamps are bounded below 2^53; map driver bigint values to JS numbers.
  return (await sqlClient.begin(async (sql) => {
    await sql`select pg_advisory_xact_lock(786483210)`;
    const store: Store = {
      async all<K extends Table>(table: K): Promise<Tables[K][]> {
        const rows = await sql`select * from ${sql(String(table))}`;
        return rows.map((row) =>
          Object.fromEntries(
            Object.entries(row).map(([key, value]) => [
              key,
              typeof value === "bigint" ? Number(value) : value,
            ]),
          ),
        ) as Tables[K][];
      },
      async put<K extends Table>(table: K, row: Tables[K]) {
        const columns = Object.keys(row).filter((k) => k !== "id");
        const values = row as unknown as Record<
          string,
          postgres.SerializableParameter
        >;
        await sql`insert into ${sql(String(table))} ${sql(values)} on conflict (id) do update set ${sql(values, ...columns)}`;
      },
    };
    return fn(store);
  })) as T;
}
