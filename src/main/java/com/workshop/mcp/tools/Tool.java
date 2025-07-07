package com.workshop.mcp.tools;

import com.workshop.mcp.spec.InputSchema;
import com.workshop.mcp.spec.JsonRpcRequest;
import com.workshop.mcp.spec.ToolCallParams;
import com.workshop.mcp.spec.ToolCallResult;

public interface Tool {

    String name();
    String description();

    InputSchema schema();

    ToolCallResult call(ToolCallParams toolCallParams);

}
