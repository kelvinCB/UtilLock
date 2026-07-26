import { AuthError, requireUser } from "../_shared/auth.ts";
import { sql } from "../_shared/db.ts";
import { handleOptions, json } from "../_shared/http.ts";

Deno.serve(async (request) => {
  const preflight = handleOptions(request);
  if (preflight) return preflight;
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  try {
    const user = await requireUser(request);
    const body = await request.json().catch(() => ({}));
    const locale = body.locale === "en" ? "en" : "es";
    const entitlements = await sql`
      select premium, premium_expires_at, free_ai_grants_used
      from public.entitlements where user_id = ${user.id}
    `;
    if (!entitlements.length) return json({ error: "profile_not_ready" }, 409);
    const entitlement = entitlements[0];
    const premium = entitlement.premium &&
      (!entitlement.premium_expires_at || new Date(entitlement.premium_expires_at).getTime() > Date.now());
    const usage = await sql`
      select count(*)::int as count from private.ai_usage
      where user_id = ${user.id} and created_at >= date_trunc('day', now())
    `;
    const cloudAllowed = premium
      ? usage[0].count < 30
      : entitlement.free_ai_grants_used < 4 && usage[0].count < 12;
    const challenges = await sql`
      select id, title, pseudocode, expected_answer
      from private.challenge_bank
      where active and locale = ${locale}
      order by random() limit 1
    `;
    if (!challenges.length) return json({ error: "challenge_bank_empty" }, 503);
    const challenge = challenges[0];
    const waitMinutes = cloudAllowed || premium ? 0 : 15;
    const expiryMinutes = cloudAllowed ? 10 : waitMinutes + 10;
    const sessions = await sql`
      insert into private.challenge_sessions (user_id, challenge_id, local_available_at, expires_at)
      values (
        ${user.id}, ${challenge.id},
        ${cloudAllowed ? null : sql`now() + (${waitMinutes} * interval '1 minute')`},
        now() + (${expiryMinutes} * interval '1 minute')
      )
      returning id, nonce, expires_at, local_available_at
    `;
    const session = sessions[0];
    return json({
      mode: cloudAllowed ? "cloud" : "local_wait",
      session_id: session.id,
      nonce: session.nonce,
      title: challenge.title,
      pseudocode: challenge.pseudocode,
      ...(cloudAllowed ? {} : { expected_answer: challenge.expected_answer }),
      attempts_remaining: 3,
      expires_at: Math.floor(new Date(session.expires_at).getTime() / 1000),
      local_available_at: session.local_available_at
        ? Math.floor(new Date(session.local_available_at).getTime() / 1000)
        : 0,
    });
  } catch (error) {
    if (error instanceof AuthError) return json({ error: "unauthorized" }, 401);
    console.error("challenge-start", error instanceof Error ? error.message : error);
    return json({ error: "internal_error" }, 500);
  }
});
