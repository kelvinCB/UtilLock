const PHOTO_BUCKET = "challenge-photos";
const MAX_PHOTO_BYTES = 1_536_000;
const MIN_PHOTO_BYTES = 1_024;

type SupportedMimeType = "image/jpeg" | "image/png";

function serverConfig(): { url: string; key: string } {
  const url = Deno.env.get("SUPABASE_URL");
  const legacyKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const secretKeys = Deno.env.get("SUPABASE_SECRET_KEYS");
  let key = legacyKey;
  if (!key && secretKeys) {
    const parsed = JSON.parse(secretKeys) as Record<string, string>;
    key = parsed.default ?? Object.values(parsed)[0];
  }
  if (!url || !key) throw new Error("Supabase Storage server credentials are incomplete");
  return { url: url.replace(/\/$/, ""), key };
}

function objectPath(path: string): string {
  return path.split("/").map(encodeURIComponent).join("/");
}

function headers(key: string, contentType = "application/json"): HeadersInit {
  return {
    apikey: key,
    Authorization: `Bearer ${key}`,
    "Content-Type": contentType,
    "Cache-Control": "no-store",
  };
}

export function decodeAndValidatePhoto(
  encoded: unknown,
  declaredMimeType: unknown,
): { bytes: Uint8Array; mimeType: SupportedMimeType } {
  if (typeof encoded !== "string" || typeof declaredMimeType !== "string") {
    throw new PhotoValidationError("invalid_photo");
  }
  if (encoded.length > 2_100_000 || !/^[A-Za-z0-9+/]+={0,2}$/.test(encoded)) {
    throw new PhotoValidationError("image_too_large");
  }
  let bytes: Uint8Array;
  try {
    const binary = atob(encoded);
    bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
  } catch {
    throw new PhotoValidationError("invalid_photo");
  }
  if (bytes.byteLength > MAX_PHOTO_BYTES) throw new PhotoValidationError("image_too_large");
  if (bytes.byteLength < MIN_PHOTO_BYTES) throw new PhotoValidationError("image_too_small");

  const detected = bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[bytes.length - 2] === 0xff &&
      bytes[bytes.length - 1] === 0xd9
    ? "image/jpeg"
    : bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47
    ? "image/png"
    : null;
  if (!detected || detected !== declaredMimeType) throw new PhotoValidationError("invalid_photo_type");
  return { bytes, mimeType: detected };
}

export async function storeChallengePhoto(input: {
  userId: string;
  sessionId: string;
  bytes: Uint8Array;
  mimeType: SupportedMimeType;
}): Promise<string> {
  const { url, key } = serverConfig();
  const extension = input.mimeType === "image/png" ? "png" : "jpg";
  const path = `${input.userId}/${input.sessionId}/${crypto.randomUUID()}.${extension}`;
  const body = input.bytes.buffer.slice(
    input.bytes.byteOffset,
    input.bytes.byteOffset + input.bytes.byteLength,
  ) as ArrayBuffer;
  const response = await fetch(
    `${url}/storage/v1/object/${PHOTO_BUCKET}/${objectPath(path)}`,
    {
      method: "POST",
      headers: {
        ...headers(key, input.mimeType),
        "x-upsert": "false",
      },
      body,
    },
  );
  if (!response.ok) throw new Error(`Storage upload failed with status ${response.status}`);
  return path;
}

export async function removeChallengePhoto(path: string): Promise<void> {
  const { url, key } = serverConfig();
  const response = await fetch(`${url}/storage/v1/object/${PHOTO_BUCKET}`, {
    method: "DELETE",
    headers: headers(key),
    body: JSON.stringify({ prefixes: [path] }),
  });
  if (!response.ok) throw new Error(`Storage cleanup failed with status ${response.status}`);
}

export class PhotoValidationError extends Error {}
