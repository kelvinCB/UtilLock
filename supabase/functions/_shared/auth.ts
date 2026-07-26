export type AuthUser = { id: string; email?: string };

export async function requireUser(request: Request): Promise<AuthUser> {
  const authorization = request.headers.get("authorization");
  if (!authorization?.startsWith("Bearer ")) throw new AuthError("Missing bearer token");
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const apiKey = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY");
  if (!supabaseUrl || !apiKey) throw new Error("Supabase auth environment is incomplete");
  const response = await fetch(`${supabaseUrl}/auth/v1/user`, {
    headers: { Authorization: authorization, apikey: apiKey },
  });
  if (!response.ok) throw new AuthError("Invalid or expired session");
  const user = await response.json();
  if (!user?.id) throw new AuthError("Session has no user");
  return { id: user.id, email: user.email };
}

export class AuthError extends Error {}
