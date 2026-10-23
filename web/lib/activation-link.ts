import { publicDemoCodePattern } from "./public-demo";
export function activationDeepLink(params: { code?: string | string[]; token?: string | string[] }): string | null {
  const { code, token } = params;
  if (code !== undefined && token !== undefined) return null;
  if (typeof code === "string" && (/^[A-Za-z0-9_-]{32,128}$/.test(code) || publicDemoCodePattern.test(code))) return `voxa://activate?code=${code}`;
  if (typeof token === "string" && token.length <= 8192 && /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(token)) return `voxa://activate?token=${token}`;
  return null;
}
