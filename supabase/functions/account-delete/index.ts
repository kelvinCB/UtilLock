import { AuthError, requireUser } from "../_shared/auth.ts";
import { handleOptions, json } from "../_shared/http.ts";

Deno.serve(async (request) => {
  const preflight = handleOptions(request);
  if (preflight) return preflight;
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  try {
    const user = await requireUser(request);
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!supabaseUrl || !serviceRoleKey) throw new Error("Supabase admin environment is incomplete");
    const response = await fetch(`${supabaseUrl}/auth/v1/admin/users/${encodeURIComponent(user.id)}`, {
      method: "DELETE",
      headers: {
        apikey: serviceRoleKey,
        Authorization: `Bearer ${serviceRoleKey}`,
      },
    });
    if (!response.ok) throw new Error(`Auth deletion failed: ${response.status}`);
    return json({ deleted: true });
  } catch (error) {
    if (error instanceof AuthError) return json({ error: "unauthorized" }, 401);
    console.error("account-delete", error instanceof Error ? error.message : error);
    return json({ error: "deletion_failed" }, 500);
  }
});
