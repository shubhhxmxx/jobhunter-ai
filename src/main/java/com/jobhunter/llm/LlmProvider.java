package com.jobhunter.llm;

/** Pluggable LLM provider. Eligibility filtering never goes through this interface. */
public interface LlmProvider {
    String id();

    String complete(String apiKey, String model, String system, String user) throws Exception;
}
