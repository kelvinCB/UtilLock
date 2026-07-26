export type Evaluation = {
  answer_visible: boolean;
  transcribed_answer: string;
  computed_answer: string;
  matches_expected: boolean;
  confidence: number;
  reason: string;
};

export async function evaluatePhoto(input: {
  imageBase64: string;
  mimeType: "image/jpeg" | "image/png";
  pseudocode: string;
  expectedAnswer: string;
  nonce: string;
}): Promise<{ evaluation: Evaluation; model: string }> {
  const apiKey = Deno.env.get("OPENAI_API_KEY");
  if (!apiKey) throw new Error("OPENAI_API_KEY is not configured");
  const model = Deno.env.get("OPENAI_EVALUATION_MODEL") ?? "gpt-5.6-terra";
  const prompt = [
    "You verify a handwritten answer to a short programming-logic exercise.",
    "Read only the final answer written in the photo. Independently execute the pseudocode.",
    "Do not accept screenshots, an empty page, the prompt itself, or an answer that is not visibly handwritten.",
    `Session nonce: ${input.nonce}`,
    `Pseudocode:\n${input.pseudocode}`,
    `Server expected answer: ${input.expectedAnswer}`,
    "Return the structured verdict. Keep reason under 160 characters and do not include personal data.",
  ].join("\n\n");

  const response = await fetch("https://api.openai.com/v1/responses", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model,
      store: false,
      reasoning: { effort: "low" },
      input: [{
        role: "user",
        content: [
          { type: "input_text", text: prompt },
          {
            type: "input_image",
            image_url: `data:${input.mimeType};base64,${input.imageBase64}`,
            detail: "high",
          },
        ],
      }],
      text: {
        format: {
          type: "json_schema",
          name: "challenge_evaluation",
          strict: true,
          schema: {
            type: "object",
            additionalProperties: false,
            properties: {
              answer_visible: { type: "boolean" },
              transcribed_answer: { type: "string" },
              computed_answer: { type: "string" },
              matches_expected: { type: "boolean" },
              confidence: { type: "number", minimum: 0, maximum: 1 },
              reason: { type: "string" },
            },
            required: [
              "answer_visible",
              "transcribed_answer",
              "computed_answer",
              "matches_expected",
              "confidence",
              "reason",
            ],
          },
        },
      },
    }),
  });
  if (!response.ok) {
    const message = await response.text();
    throw new Error(`OpenAI response ${response.status}: ${message.slice(0, 300)}`);
  }
  const payload = await response.json();
  const outputText = payload.output_text ?? payload.output
    ?.flatMap((item: { content?: Array<{ type: string; text?: string }> }) => item.content ?? [])
    ?.find((item: { type: string }) => item.type === "output_text")?.text;
  if (!outputText) throw new Error("OpenAI returned no structured output");
  return { evaluation: JSON.parse(outputText) as Evaluation, model };
}

export function normalizeAnswer(value: string): string {
  return value.trim().toLocaleLowerCase("en-US").replace(",", ".").replace(/[^a-z0-9.-]/g, "");
}
