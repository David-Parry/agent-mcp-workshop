package com.workshop.mcp.spec;

import java.util.List;

public record ModelPreferences( List<ModelHint> hints,
                                Double costPriority,
                                Double speedPriority,
                                Double intelligencePriority) {
}
