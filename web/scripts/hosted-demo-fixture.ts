import { readFileSync, writeFileSync } from "node:fs";
import { createPrivateKey, createPublicKey } from "node:crypto";
import { resolve } from "node:path";
import { issuePublicDemo, redeemPublicDemo } from "../lib/public-demo";
import { renderDemoQr } from "../lib/demo-qr";
const root = resolve(import.meta.dirname, "../..");
const pem = readFileSync(resolve(root, ".local/hosted-demo-private.pem"), "utf8");
process.env.VOXA_PUBLIC_DEMO = "true";
process.env.VOXA_PUBLIC_DEMO_KEY_ID = "hosted-demo-v1";
process.env.VOXA_PUBLIC_DEMO_PRIVATE_KEY = pem;
const deviceId = "56666666-6666-4666-8666-666666666666";
const now = Date.now();
const publicKey = Buffer.from(createPublicKey(createPrivateKey(pem)).export({ format: "jwk" }).x!, "base64url").toString("base64");
const tokens = Object.fromEntries(["FAMILY_PRO", "CLINICAL_SLP"].map(tier => [tier, redeemPublicDemo(issuePublicDemo({ tier }).code, deviceId, now).token]));
writeFileSync(resolve(root, "config/hosted-demo-test-fixture.json"), JSON.stringify({ deviceId, nowSeconds: Math.floor(now / 1000), publicKey, tokens }, null, 2) + "\n");
console.log("Public interoperability fixture written for a synthetic installation.");
async function writeQrFixtures() {
for (const tier of ["FAMILY_PRO", "CLINICAL_SLP"]) {
  const { code } = issuePublicDemo({ tier });
  const dataUrl = await renderDemoQr("https://voxa-web-pi.vercel.app", code);
  writeFileSync(resolve(root, `config/qr-${tier.toLowerCase()}-demo.png`), Buffer.from(dataUrl.split(",")[1], "base64"));
}
}
writeQrFixtures().catch(() => { console.error("Could not generate public QR fixtures"); process.exitCode = 1; });
