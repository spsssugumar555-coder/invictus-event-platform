package com.invictus.platform.ai;

/** Abstraction over the LLM provider. All calls happen on the server; the browser never sees keys or prompts. */
public interface AiClient {
    /**
     * @param system   system instruction
     * @param prompt   user prompt
     * @param image    optional image bytes (may be null)
     * @param imageMime MIME type of the image when present
     * @param json     true to force a JSON response, false for free text / HTML
     * @return the model's raw text
     */
    String generate(String system, String prompt, byte[] image, String imageMime, boolean json);
}
