import postgres from "postgres";

const databaseUrl = Deno.env.get("SUPABASE_DB_URL");
if (!databaseUrl) {
  throw new Error("SUPABASE_DB_URL is required (use the transaction pooler URL in production)");
}

export const sql = postgres(databaseUrl, {
  max: 2,
  prepare: false,
  idle_timeout: 20,
  connect_timeout: 10,
});
