import { assertEquals } from "@std/assert";
import { decodeAndValidatePhoto, PhotoValidationError } from "./storage.ts";

function encoded(bytes: number[]): string {
  return btoa(String.fromCharCode(...bytes));
}

Deno.test("decodeAndValidatePhoto accepts a bounded JPEG", () => {
  const body = [0xff, 0xd8, ...Array(1020).fill(0), 0xff, 0xd9];
  const result = decodeAndValidatePhoto(encoded(body), "image/jpeg");
  assertEquals(result.mimeType, "image/jpeg");
  assertEquals(result.bytes.byteLength, 1024);
});

Deno.test("decodeAndValidatePhoto rejects mismatched content", () => {
  const body = [0xff, 0xd8, ...Array(1020).fill(0), 0xff, 0xd9];
  let message = "";
  try {
    decodeAndValidatePhoto(encoded(body), "image/png");
  } catch (error) {
    if (error instanceof PhotoValidationError) message = error.message;
  }
  assertEquals(message, "invalid_photo_type");
});
