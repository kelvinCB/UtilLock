import { handleOptions, json } from "../_shared/http.ts";

Deno.serve((request) => {
  const preflight = handleOptions(request);
  if (preflight) return preflight;
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  return json({
    version: 1,
    adult_doh_url: "https://family.cloudflare-dns.com/dns-query",
    standard_doh_url: "https://cloudflare-dns.com/dns-query",
    supported_browsers: [
      "com.android.chrome",
      "com.brave.browser",
      "com.microsoft.emmx",
      "org.mozilla.firefox",
      "com.sec.android.app.sbrowser",
    ],
  });
});
