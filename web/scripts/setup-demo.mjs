import { generateKeyPairSync, randomBytes, sign } from "node:crypto";
import { mkdir, readFile, writeFile, copyFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
let existingEnv = "";
try {
  existingEnv = await readFile(resolve(root, "web/.env.local"), "utf8");
} catch (e) {
  if (e.code !== "ENOENT") throw e;
}
if (existingEnv && !/^VOXA_DEMO_MODE=true\s*$/m.test(existingEnv))
  throw new Error(
    "Existing .env.local is not a local demo. Preserve it and configure production keys explicitly.",
  );
await mkdir(resolve(root, ".local"), { recursive: true });
await mkdir(resolve(root, "config"), { recursive: true });
let privateKey;
try {
  privateKey = await readFile(
    resolve(root, ".local/license-private.pem"),
    "utf8",
  );
} catch {
  const pair = generateKeyPairSync("ed25519", {
    publicKeyEncoding: { type: "spki", format: "pem" },
    privateKeyEncoding: { type: "pkcs8", format: "pem" },
  });
  privateKey = pair.privateKey;
  await writeFile(resolve(root, ".local/license-private.pem"), privateKey, {
    mode: 0o600,
  });
}
const { createPublicKey } = await import("node:crypto");
const publicKey = createPublicKey(privateKey);
const publicPem = publicKey.export({ type: "spki", format: "pem" }).toString();
const raw = publicKey
  .export({ type: "spki", format: "der" })
  .subarray(-32)
  .toString("base64");
await writeFile(
  resolve(root, "config/demo-license-public-key.properties"),
  `kid=demo-v1\npublicKey=${raw}\n`,
);
try {
  await readFile(resolve(root, "web/.env.local"));
} catch {
  await writeFile(
    resolve(root, "web/.env.local"),
    [
      "VOXA_DEMO_MODE=true",
      "APP_URL=http://127.0.0.1:3000",
      `VOXA_SESSION_SECRET=${randomBytes(32).toString("hex")}`,
      "VOXA_LICENSE_KEY_ID=demo-v1",
      `VOXA_LICENSE_PRIVATE_KEY="${privateKey.trim().replaceAll("\n", "\\n")}"`,
      `VOXA_LICENSE_PUBLIC_KEYS='${JSON.stringify({ "demo-v1": publicPem })}'`,
    ].join("\n") + "\n",
    { mode: 0o600 },
  );
}
// A signed, non-secret interoperability fixture. It grants access only to this test device.
const header = Buffer.from(
  JSON.stringify({ alg: "EdDSA", typ: "JWT", kid: "demo-v1" }),
).toString("base64url");
const claims = {
  iss: "voxa-license",
  aud: "voxa-android",
  sub: "11111111-1111-4111-8111-111111111111",
  jti: "22222222-2222-4222-8222-222222222222",
  key: "VOXA-1111-2222-3333-4444",
  tier: "FAMILY_PRO",
  device_id: "33333333-3333-4333-8333-333333333333",
  devices_allowed: 2,
  iat: Math.floor(Date.now() / 1000) - 60,
  exp: Math.floor(Date.now() / 1000) + 30 * 86400,
  grace_until: Math.floor(Date.now() / 1000) + 37 * 86400,
};
const body = `${header}.${Buffer.from(JSON.stringify(claims)).toString("base64url")}`;
await writeFile(
  resolve(root, "config/license-test-fixture.json"),
  JSON.stringify(
    {
      publicKey: raw,
      deviceId: claims.device_id,
      nowSeconds: claims.iat + 100,
      token: `${body}.${sign(null, Buffer.from(body), privateKey).toString("base64url")}`,
    },
    null,
    2,
  ) + "\n",
);
await mkdir(resolve(root, "web/public/demo-audio"), { recursive: true });
for (const word of ["water", "milk", "mama", "help"]) {
  await copyFile(
    resolve(root, `Voxa/app/src/main/assets/practice/${word}_ref.wav`),
    resolve(root, `web/public/demo-audio/${word}.wav`),
  );
}
console.log(
  "Local demo configured. Private keys remain ignored in .local/ and web/.env.local.",
);
console.log(
  "Android debug builds trust config/demo-license-public-key.properties. Release builds do not.",
);
