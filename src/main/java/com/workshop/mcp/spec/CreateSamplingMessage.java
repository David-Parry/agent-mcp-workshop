package com.workshop.mcp.spec;

import java.util.List;
import java.util.Map;

public record CreateSamplingMessage(List<Message> messages,
                                     ModelPreferences modelPreferences,
                                     String systemPrompt,
                                     String includeContext,
                                     Double temperature,
                                     int maxTokens,
                                     List<String> stopSequences,
                                     Map<String, Object> metadata) {
}
