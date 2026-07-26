-- Challenge photos are short-lived server-side work items. The bucket is
-- private and has no client RLS policies, so only trusted server credentials
-- can read, write, or delete its objects.
insert into storage.buckets (
  id,
  name,
  public,
  file_size_limit,
  allowed_mime_types
) values (
  'challenge-photos',
  'challenge-photos',
  false,
  1536000,
  array['image/jpeg', 'image/png']::text[]
)
on conflict (id) do update
set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;
