import { mkdir, writeFile } from "node:fs/promises";
const fingerprint = process.env.VOXA_ANDROID_CERT_SHA256;
const applicationId =
  process.env.VOXA_ANDROID_APPLICATION_ID ?? "com.example.voxa";
if (!fingerprint || !/^([A-Fa-f0-9]{2}:){31}[A-Fa-f0-9]{2}$/.test(fingerprint))
  throw new Error(
    "Set VOXA_ANDROID_CERT_SHA256 to the production signing certificate fingerprint.",
  );
if (!/^[a-zA-Z][\w]*(\.[a-zA-Z][\w]*)+$/.test(applicationId))
  throw new Error("Invalid Android application ID");
const dir = new URL("../public/.well-known/", import.meta.url);
await mkdir(dir, { recursive: true });
await writeFile(
  new URL("assetlinks.json", dir),
  JSON.stringify(
    [
      {
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: applicationId,
          sha256_cert_fingerprints: [fingerprint.toUpperCase()],
        },
      },
    ],
    null,
    2,
  ) + "\n",
);
console.log(
  "Digital Asset Links generated. Deploy on the same HTTPS host configured in the Android build.",
);
