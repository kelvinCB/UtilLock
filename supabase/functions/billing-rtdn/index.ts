import { sql } from "../_shared/db.ts";
import { handleOptions, json } from "../_shared/http.ts";
import { persistSubscription, verifySubscription } from "../_shared/google-play.ts";

async function verifyPubSubIdentity(request: Request): Promise<boolean> {
  const authorization = request.headers.get("authorization");
  const token = authorization?.startsWith("Bearer ") ? authorization.slice(7) : "";
  const audience = Deno.env.get("GOOGLE_PUBSUB_AUDIENCE");
  const expectedEmail = Deno.env.get("GOOGLE_PUBSUB_SERVICE_ACCOUNT_EMAIL");
  if (!token || !audience || !expectedEmail) return false;
  const response = await fetch(
    `https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(token)}`,
  );
  if (!response.ok) return false;
  const identity = await response.json();
  return identity.aud === audience && identity.email === expectedEmail &&
    (identity.email_verified === "true" || identity.email_verified === true);
}

Deno.serve(async (request) => {
  const preflight = handleOptions(request);
  if (preflight) return preflight;
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  if (!await verifyPubSubIdentity(request)) return json({ error: "unauthorized" }, 401);
  try {
    const envelope = await request.json();
    const messageId = envelope.message?.messageId;
    const encoded = envelope.message?.data;
    if (!messageId || !encoded) return json({ error: "invalid_pubsub_message" }, 400);
    const payload = JSON.parse(
      new TextDecoder().decode(Uint8Array.from(atob(encoded), (character) => character.charCodeAt(0))),
    );
    const notification = payload.subscriptionNotification;
    const purchaseToken = notification?.purchaseToken;
    const eventType = notification?.notificationType;
    await sql`
      insert into private.billing_events (message_id, event_type, purchase_token, payload)
      values (${messageId}, ${eventType ?? null}, ${purchaseToken ?? null}, ${sql.json(payload)})
      on conflict (message_id) do nothing
    `;
    if (!purchaseToken) return json({ ok: true });
    const purchases = await sql`
      select user_id, product_id from private.play_purchases where purchase_token = ${purchaseToken}
    `;
    if (!purchases.length) return json({ ok: true, unlinked: true });
    const purchase = purchases[0];
    const verification = await verifySubscription(purchaseToken, purchase.product_id);
    await persistSubscription(purchase.user_id, purchaseToken, purchase.product_id, verification);
    return json({ ok: true });
  } catch (error) {
    console.error("billing-rtdn", error instanceof Error ? error.message : error);
    return json({ error: "processing_failed" }, 500);
  }
});
