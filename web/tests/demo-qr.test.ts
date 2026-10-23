import test from "node:test";
import assert from "node:assert/strict";
import jsQR from "jsqr";
import { PNG } from "pngjs";
import { demoActivationUrl, renderDemoQr } from "../lib/demo-qr";

test("actual QR pixels decode at phone and desktop display sizes", async () => {
  const origin = "https://voxa-web-pi.vercel.app", code = "VOXA-F-001-A1B2C3D4";
  for (const width of [240, 360, 640]) {
    const url = await renderDemoQr(origin, code, width);
    const png = PNG.sync.read(Buffer.from(url.split(",")[1], "base64"));
    const decoded = jsQR(new Uint8ClampedArray(png.data), png.width, png.height);
    assert.ok(decoded, `QR failed to decode at ${width}px`);
    assert.equal(decoded.data, demoActivationUrl(origin, code));
    assert.ok(decoded.version <= 5, "Demo QR must remain small enough to scan easily");
  }
});
