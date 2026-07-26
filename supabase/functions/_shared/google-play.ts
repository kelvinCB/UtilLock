import { sql } from "./db.ts";

type ServiceAccount = { client_email: string; private_key: string; token_uri?: string };

export type SubscriptionVerification = {
  active: boolean;
  state: string;
  expiresAt: string | null;
  raw: Record<string, unknown>;
};

function base64Url(value: Uint8Array | string): string {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : value;
  let binary = "";
  bytes.forEach((byte) => binary += String.fromCharCode(byte));
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

async function googleAccessToken(): Promise<string> {
  const raw = Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON");
  if (!raw) throw new Error("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is not configured");
  const account = JSON.parse(raw) as ServiceAccount;
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64Url(JSON.stringify({
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: account.token_uri ?? "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const signingInput = `${header}.${claims}`;
  const pem = account.private_key.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
  const keyBytes = Uint8Array.from(atob(pem), (character) => character.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    keyBytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signingInput),
  );
  const assertion = `${signingInput}.${base64Url(new Uint8Array(signature))}`;
  const response = await fetch(account.token_uri ?? "https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) throw new Error(`Google OAuth failed: ${response.status}`);
  return (await response.json()).access_token;
}

export async function verifySubscription(
  purchaseToken: string,
  expectedProductId: string,
): Promise<SubscriptionVerification> {
  const packageName = Deno.env.get("ANDROID_PACKAGE_NAME") ?? "app.utillock.android";
  const token = await googleAccessToken();
  const endpoint = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${
    encodeURIComponent(packageName)
  }/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
  const response = await fetch(endpoint, { headers: { Authorization: `Bearer ${token}` } });
  if (!response.ok) throw new Error(`Google Play verification failed: ${response.status}`);
  const raw = await response.json();
  const matchingItems = (raw.lineItems ?? []).filter((item: { productId?: string }) =>
    item.productId === expectedProductId
  );
  const expiries = matchingItems.map((item: { expiryTime?: string }) => item.expiryTime).filter(Boolean)
    .sort();
  const expiresAt = expiries.at(-1) ?? null;
  const state = raw.subscriptionState ?? "SUBSCRIPTION_STATE_UNSPECIFIED";
  const entitledStates = new Set([
    "SUBSCRIPTION_STATE_ACTIVE",
    "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
    "SUBSCRIPTION_STATE_CANCELED",
  ]);
  const active = matchingItems.length > 0 && entitledStates.has(state) && !!expiresAt &&
    new Date(expiresAt).getTime() > Date.now();
  return { active, state, expiresAt, raw };
}

export async function persistSubscription(
  userId: string,
  purchaseToken: string,
  productId: string,
  verification: SubscriptionVerification,
): Promise<void> {
  await sql.begin(async (transaction) => {
    await transaction`
      insert into private.play_purchases (
        purchase_token, user_id, product_id, subscription_state, active, expires_at, raw_response
      ) values (
        ${purchaseToken}, ${userId}, ${productId}, ${verification.state}, ${verification.active},
        ${verification.expiresAt}, ${JSON.stringify(verification.raw)}::jsonb
      )
      on conflict (purchase_token) do update set
        user_id = excluded.user_id,
        product_id = excluded.product_id,
        subscription_state = excluded.subscription_state,
        active = excluded.active,
        expires_at = excluded.expires_at,
        raw_response = excluded.raw_response,
        last_verified_at = now()
    `;
    const summary = await transaction`
      select coalesce(bool_or(active and expires_at > now()), false) as premium,
             max(expires_at) as expires_at
      from private.play_purchases
      where user_id = ${userId}
    `;
    await transaction`
      update public.entitlements
      set premium = ${summary[0].premium}, premium_expires_at = ${summary[0].expires_at}, updated_at = now()
      where user_id = ${userId}
    `;
  });
}
