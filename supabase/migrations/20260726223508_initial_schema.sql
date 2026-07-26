create extension if not exists pgcrypto with schema extensions;

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table public.profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text check (char_length(display_name) <= 80),
  locale text not null default 'es' check (locale in ('es', 'en')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.entitlements (
  user_id uuid primary key references auth.users(id) on delete cascade,
  premium boolean not null default false,
  premium_expires_at timestamptz,
  free_ai_grants_used smallint not null default 0 check (free_ai_grants_used between 0 and 4),
  updated_at timestamptz not null default now()
);

create table public.filter_config (
  id boolean primary key default true check (id),
  config_version integer not null default 1,
  adult_doh_url text not null,
  standard_doh_url text not null,
  supported_browsers jsonb not null default '[]'::jsonb,
  updated_at timestamptz not null default now()
);

create table private.challenge_bank (
  id uuid primary key default gen_random_uuid(),
  locale text not null check (locale in ('es', 'en')),
  title text not null,
  pseudocode text not null,
  expected_answer text not null,
  difficulty smallint not null default 1 check (difficulty between 1 and 3),
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create table private.challenge_sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  challenge_id uuid not null references private.challenge_bank(id),
  nonce uuid not null default gen_random_uuid(),
  attempts_used smallint not null default 0 check (attempts_used between 0 and 3),
  accepted boolean not null default false,
  consumed_at timestamptz,
  local_available_at timestamptz,
  expires_at timestamptz not null default (now() + interval '10 minutes'),
  created_at timestamptz not null default now()
);

create index challenge_sessions_user_created_idx
  on private.challenge_sessions (user_id, created_at desc);

create table private.ai_usage (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  session_id uuid not null references private.challenge_sessions(id) on delete cascade,
  accepted boolean not null,
  model text not null,
  created_at timestamptz not null default now(),
  unique (session_id, created_at)
);

create index ai_usage_user_day_idx on private.ai_usage (user_id, created_at desc);

create table private.play_purchases (
  purchase_token text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  product_id text not null,
  subscription_state text not null,
  active boolean not null default false,
  expires_at timestamptz,
  last_verified_at timestamptz not null default now(),
  raw_response jsonb not null default '{}'::jsonb
);

create index play_purchases_user_idx on private.play_purchases (user_id);

create table private.billing_events (
  message_id text primary key,
  event_type integer,
  purchase_token text,
  payload jsonb not null,
  processed_at timestamptz not null default now()
);

create table private.rate_limits (
  bucket text not null,
  subject text not null,
  window_started_at timestamptz not null,
  counter integer not null default 1,
  primary key (bucket, subject, window_started_at)
);

alter table public.profiles enable row level security;
alter table public.entitlements enable row level security;
alter table public.filter_config enable row level security;
alter table private.challenge_bank enable row level security;
alter table private.challenge_sessions enable row level security;
alter table private.ai_usage enable row level security;
alter table private.play_purchases enable row level security;
alter table private.billing_events enable row level security;
alter table private.rate_limits enable row level security;

create policy "profiles_select_own"
  on public.profiles for select to authenticated
  using ((select auth.uid()) = user_id);

create policy "profiles_update_own"
  on public.profiles for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create policy "entitlements_select_own"
  on public.entitlements for select to authenticated
  using ((select auth.uid()) = user_id);

create policy "filter_config_read"
  on public.filter_config for select to anon, authenticated
  using (true);

-- Supabase projects created after 2026-04-28 do not expose new tables
-- automatically. Grants are explicit and intentionally separate from RLS.
revoke all on public.profiles from anon, authenticated;
revoke all on public.entitlements from anon, authenticated;
revoke all on public.filter_config from anon, authenticated;
grant usage on schema public to anon, authenticated, service_role;
grant select on public.profiles to authenticated;
grant update (display_name, locale, updated_at) on public.profiles to authenticated;
grant select on public.entitlements to authenticated;
grant select on public.filter_config to anon, authenticated;

grant usage on schema private to service_role;
grant all on all tables in schema private to service_role;
grant usage, select on all sequences in schema private to service_role;

create or replace function private.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (user_id) values (new.id);
  insert into public.entitlements (user_id) values (new.id);
  return new;
end;
$$;

revoke all on function private.handle_new_user() from public, anon, authenticated;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function private.handle_new_user();

insert into public.filter_config (
  id,
  adult_doh_url,
  standard_doh_url,
  supported_browsers
) values (
  true,
  'https://family.cloudflare-dns.com/dns-query',
  'https://cloudflare-dns.com/dns-query',
  '["com.android.chrome","com.brave.browser","com.microsoft.emmx","org.mozilla.firefox","com.sec.android.app.sbrowser"]'::jsonb
);

insert into private.challenge_bank (locale, title, pseudocode, expected_answer, difficulty) values
  ('es', '¿Qué valor imprime el algoritmo?', E'suma = 0\nPARA n EN [2, 5, 8, 11]:\n    SI n MOD 2 == 0:\n        suma = suma + n\nIMPRIMIR suma', '10', 1),
  ('es', '¿Qué valor imprime el algoritmo?', E'a = 1\nb = 1\nREPETIR 5 VECES:\n    c = a + b\n    a = b\n    b = c\nIMPRIMIR b', '13', 1),
  ('es', '¿Qué valor imprime el algoritmo?', E'contador = 0\nPARA i DESDE 1 HASTA 12:\n    SI i MOD 3 == 0:\n        contador = contador + 1\nIMPRIMIR contador', '4', 1),
  ('es', '¿Qué valor imprime el algoritmo?', E'valor = 20\nMIENTRAS valor > 3:\n    valor = valor - 4\nIMPRIMIR valor', '0', 1),
  ('en', 'What value does the algorithm print?', E'total = 0\nFOR n IN [2, 5, 8, 11]:\n    IF n MOD 2 == 0:\n        total = total + n\nPRINT total', '10', 1),
  ('en', 'What value does the algorithm print?', E'a = 1\nb = 1\nREPEAT 5 TIMES:\n    c = a + b\n    a = b\n    b = c\nPRINT b', '13', 1);
