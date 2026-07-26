import { assertEquals } from "@std/assert";
import { acceptsEvaluation, normalizeAnswer } from "./openai.ts";

Deno.test("normalizeAnswer accepts formatting but not extra text", () => {
  assertEquals(normalizeAnswer(" 13\n"), "13");
  assertEquals(normalizeAnswer("-2,5"), "-2.5");
  assertEquals(normalizeAnswer("Respuesta: 4"), "respuesta4");
});

Deno.test("acceptsEvaluation requires readable sufficient handwriting and deterministic agreement", () => {
  const valid = {
    photo_readable: true,
    handwritten_answer_present: true,
    answer_sufficient: true,
    transcribed_answer: "13",
    computed_answer: "13",
    matches_expected: true,
    confidence: 0.92,
    reason: "Readable and correct.",
  };
  assertEquals(acceptsEvaluation(valid, "13"), true);
  assertEquals(acceptsEvaluation({ ...valid, photo_readable: false }, "13"), false);
  assertEquals(acceptsEvaluation({ ...valid, transcribed_answer: "12" }, "13"), false);
  assertEquals(acceptsEvaluation({ ...valid, confidence: 0.74 }, "13"), false);
});
