import QRCode from "qrcode";

export function demoActivationUrl(origin: string, code: string): string {
  if (!/^VOXA-[FC]-\d{3}-[A-F0-9]{8}$/.test(code)) throw new Error("Invalid demo code");
  return `${new URL(origin).origin}/activate?code=${code}`;
}
export function renderDemoQr(origin: string, code: string, width = 640): Promise<string> {
  return QRCode.toDataURL(demoActivationUrl(origin, code), {
    width, margin: 4, errorCorrectionLevel: "M", color: { dark: "#09111f", light: "#ffffff" },
  });
}
