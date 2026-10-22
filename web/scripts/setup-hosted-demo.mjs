import { generateKeyPairSync, createPrivateKey, createPublicKey } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { spawnSync } from "node:child_process";

const root = resolve(import.meta.dirname, "../..");
const folder = resolve(root, ".local");
mkdirSync(folder, { recursive: true });
const privatePath = resolve(folder, "hosted-demo-private.pem");
if (!existsSync(privatePath)) {
  const { privateKey } = generateKeyPairSync("ed25519");
  writeFileSync(privatePath, privateKey.export({ type: "pkcs8", format: "pem" }), { mode: 0o600 });
}
const pem = readFileSync(privatePath, "utf8");
const publicKey = createPublicKey(createPrivateKey(pem));
const raw = Buffer.from(publicKey.export({ format: "jwk" }).x, "base64url").toString("base64");
const kid = "hosted-demo-v1";
writeFileSync(resolve(root, "config/hosted-demo-license-public-key.properties"), `kid=${kid}\npublicKey=${raw}\n`);
const vars = {
  VOXA_PUBLIC_DEMO: "true",
  VOXA_PUBLIC_DEMO_KEY_ID: kid,
  VOXA_PUBLIC_DEMO_PRIVATE_KEY: pem.replace(/\n/g, "\\n"),
};
const envPath = resolve(root, "web/.env.local");
let local = existsSync(envPath) ? readFileSync(envPath, "utf8") : "";
for (const name of Object.keys(vars)) local = local.replace(new RegExp(`^${name}=.*\\r?\\n?`, "gm"), "");
local += "\n" + Object.entries(vars).map(([name, value]) =>
  `${name}=${name.includes("PRIVATE_KEY") ? '"' + value + '"' : JSON.stringify(value)}`).join("\n") + "\n";
writeFileSync(envPath, local, { mode: 0o600 });
if (process.argv.includes("--configure-vercel")) {
  for (const [name, value] of Object.entries({ ...vars, APP_URL: "https://voxa-web-pi.vercel.app" })) {
    // Only fixed names/options are shell arguments. Secret values travel over stdin, never output.
    const args = ["--yes", "vercel@65.1.0", "env", "add", name, "--environment", "production", "--force", "--yes",
      name.includes("PRIVATE_KEY") ? "--sensitive" : "--no-sensitive"];
    const result = spawnSync("npx.cmd", args, {
      cwd: resolve(root, "web"), shell: true, input: value, encoding: "utf8", stdio: ["pipe", "pipe", "pipe"],
    });
    if (result.status !== 0) {
      console.error(`Could not configure ${name}. CLI exit status: ${result.status ?? "unavailable"}.`);
      process.exit(1);
    }
    console.log(`Configured ${name} (value hidden)`);
  }
}
console.log("Hosted demo key prepared. Only its public key is included in the demo APK.");
