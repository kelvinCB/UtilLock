import { AuthError, requireUser } from "../_shared/auth.ts";
import { sql } from "../_shared/db.ts";
import { handleOptions, json } from "../_shared/http.ts";
import { acceptsEvaluation, evaluatePhoto } from "../_shared/openai.ts";
import {
  decodeAndValidatePhoto,
  PhotoValidationError,
  removeChallengePhoto,
  storeChallengePhoto,
} from "../_shared/storage.ts";

async function safetyIdentifier(userId: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`utillock:${userId}`));
  const hex = Array.from(new Uint8Array(digest)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
  return `utl_${hex.slice(0, 48)}`;
}

Deno.serve(async (request) => {
  const preflight = handleOptions(request);
  if (preflight) return preflight;
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  try {
    const user = await requireUser(request);
    const body = await request.json();
    if (!body.session_id || !body.image_base64 || !body.mime_type) {
      return json({ error: "invalid_request" }, 400);
    }
    const sessions = await sql`
      select s.id, s.nonce, s.attempts_used, s.expires_at, s.consumed_at,
             c.pseudocode, c.expected_answer,
             e.premium, e.premium_expires_at, e.free_ai_grants_used
      from private.challenge_sessions s
      join private.challenge_bank c on c.id = s.challenge_id
      join public.entitlements e on e.user_id = s.user_id
      where s.id = ${body.session_id} and s.user_id = ${user.id}
    `;
    if (!sessions.length) return json({ error: "challenge_not_found" }, 404);
    const session = sessions[0];
    if (session.consumed_at || new Date(session.expires_at).getTime() <= Date.now()) {
      return json({ error: "challenge_expired" }, 410);
    }
    if (session.attempts_used >= 3) return json({ error: "attempts_exhausted" }, 409);
    let photo;
    try {
      photo = decodeAndValidatePhoto(body.image_base64, body.mime_type);
    } catch (error) {
      if (error instanceof PhotoValidationError) {
        const status = error.message === "image_too_large" ? 413 : 422;
        return json({
          error: error.message,
          decision: "rejected",
          attempts_remaining: Math.max(0, 3 - session.attempts_used),
        }, status);
      }
      throw error;
    }
    const premium = session.premium &&
      (!session.premium_expires_at || new Date(session.premium_expires_at).getTime() > Date.now());
    const usage = await sql`
      select count(*)::int as daily,
             count(*) filter (where created_at > now() - interval '1 minute')::int as recent
      from private.ai_usage where user_id = ${user.id} and created_at >= date_trunc('day', now())
    `;
    const dailyLimit = premium ? 30 : 12;
    if (usage[0].daily >= dailyLimit || usage[0].recent >= 4) return json({ error: "rate_limited" }, 429);
    if (!premium && session.free_ai_grants_used >= 4) return json({ error: "trial_exhausted" }, 402);

    let photoPath: string | null = null;
    let result;
    try {
      photoPath = await storeChallengePhoto({
        userId: user.id,
        sessionId: session.id,
        bytes: photo.bytes,
        mimeType: photo.mimeType,
      });
      result = await evaluatePhoto({
        imageBase64: body.image_base64,
        mimeType: photo.mimeType,
        pseudocode: session.pseudocode,
        expectedAnswer: session.expected_answer,
        nonce: session.nonce,
        safetyIdentifier: await safetyIdentifier(user.id),
      });
    } catch (error) {
      console.error("challenge-evaluate provider", error instanceof Error ? error.message : error);
      return json({ error: "evaluation_unavailable", local_fallback: true }, 503);
    } finally {
      if (photoPath) {
        try {
          await removeChallengePhoto(photoPath);
        } catch (error) {
          console.error("challenge-evaluate cleanup", error instanceof Error ? error.message : error);
        }
      }
    }
    const evaluation = result.evaluation;
    const accepted = acceptsEvaluation(evaluation, session.expected_answer);
    const attemptsUsed = session.attempts_used + 1;

    await sql.begin(async (transaction) => {
      await transaction`
        update private.challenge_sessions
        set attempts_used = ${attemptsUsed}, accepted = ${accepted},
            consumed_at = case when ${accepted} then now() else consumed_at end
        where id = ${session.id} and consumed_at is null
      `;
      await transaction`
        insert into private.ai_usage (user_id, session_id, accepted, model)
        values (${user.id}, ${session.id}, ${accepted}, ${result.model})
      `;
      if (accepted && !premium) {
        await transaction`
          update public.entitlements
          set free_ai_grants_used = least(4, free_ai_grants_used + 1), updated_at = now()
          where user_id = ${user.id}
        `;
      }
    });
    return json({
      accepted,
      decision: accepted ? "approved" : "rejected",
      feedback: accepted ? "Respuesta correcta. Pausa concedida." : evaluation.reason,
      attempts_remaining: Math.max(0, 3 - attemptsUsed),
      quality: {
        readable: evaluation.photo_readable,
        handwritten_answer_present: evaluation.handwritten_answer_present,
        answer_sufficient: evaluation.answer_sufficient,
        confidence: evaluation.confidence,
      },
    });
  } catch (error) {
    if (error instanceof AuthError) return json({ error: "unauthorized" }, 401);
    console.error("challenge-evaluate", error instanceof Error ? error.message : error);
    return json({ error: "internal_error" }, 500);
  }
});
