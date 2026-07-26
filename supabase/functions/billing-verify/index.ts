import { AuthError, requireUser } from "../_shared/auth.ts";
import { handleOptions, json } from "../_shared/http.ts";
import { persistSubscription, verifySubscription } from "../_shared/google-play.ts";

Deno.serve(async (request) => {
  const preflight = handleOptions(request);
  if (preflight) return preflight;
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  try {
    const user = await requireUser(request);
    const body = await request.json();
    const expectedProduct = Deno.env.get("PLAY_SUBSCRIPTION_PRODUCT_ID") ?? "utillock_premium_monthly";
    if (
      body.product_id !== expectedProduct || typeof body.purchase_token !== "string" ||
      body.purchase_token.length < 20
    ) {
      return json({ error: "invalid_purchase" }, 400);
    }
    const verification = await verifySubscription(body.purchase_token, expectedProduct);
    await persistSubscription(user.id, body.purchase_token, expectedProduct, verification);
    return json({ active: verification.active, expires_at: verification.expiresAt });
  } catch (error) {
    if (error instanceof AuthError) return json({ error: "unauthorized" }, 401);
    console.error("billing-verify", error instanceof Error ? error.message : error);
    return json({ error: "verification_failed" }, 502);
  }
});
