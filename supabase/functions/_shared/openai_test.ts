import { assertEquals } from "@std/assert";
import { normalizeAnswer } from "./openai.ts";

Deno.test("normalizeAnswer accepts formatting but not extra text", () => {
  assertEquals(normalizeAnswer(" 13\n"), "13");
  assertEquals(normalizeAnswer("-2,5"), "-2.5");
  assertEquals(normalizeAnswer("Respuesta: 4"), "respuesta4");
});
