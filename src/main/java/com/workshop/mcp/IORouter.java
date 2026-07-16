package com.workshop.mcp;

import com.workshop.mcp.io.IOHandler;
import com.workshop.mcp.io.LogFile;
import com.workshop.mcp.io.LogFileWriter;
// >>> STEP 10: paste the JavadocResources import here (lessons/presentation-3hr/walkthrough.md)
import com.workshop.mcp.spec.*;
import com.workshop.mcp.spec.builders.*;
// >>> STEP 27: paste the TaskStore import here (lessons/presentation-3hr/walkthrough.md)

// >>> STEP 13: paste the KeyWordSearch import here (lessons/presentation-3hr/walkthrough.md)

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.workshop.mcp.spec.Message.KEY_WORD_MESSAGE;
import static com.workshop.mcp.spec.Resource.DEFAULT_MIME_TYPE;
import static com.workshop.mcp.spec.builders.ElicitationBuilder.buildSearchDirectoryElicitation;

public class IORouter implements Router {
    private static final LogFile logger = LogFileWriter.getInstance();
    private static final String JSON_RPC_VERSION = "2.0";
    // >>> STEP 8: paste ROOTS_REQUEST_ID and the rootsRequest constant here (lessons/presentation-3hr/walkthrough.md)
    // >>> STEP 7: paste SAMPLE_REQUEST_ID here (lessons/presentation-3hr/walkthrough.md)
    // >>> STEP 20: paste ELICITATION_REQUEST_ID here (lessons/presentation-3hr/walkthrough.md)
    private final IOHandler io;
    private final Set<String> roots = new HashSet<>();
    private final JsonRpcMessageDeserializer deserializer = new JsonRpcMessageDeserializer();
    // >>> STEP 28: paste the taskStore field here (lessons/presentation-3hr/walkthrough.md)
    // >>> STEP 6: paste the hasRoots and hasSampling flags here (lessons/presentation-3hr/walkthrough.md)
    // >>> STEP 19: paste the hasElicitation flag here (lessons/presentation-3hr/walkthrough.md)
    // >>> STEP 28: paste the hasTasks flag here (lessons/presentation-3hr/walkthrough.md)
    // >>> STEP 20: paste the pending tools/call state here (lessons/presentation-3hr/walkthrough.md)

    public IORouter(IOHandler io) {
        this.io = io;
        // >>> STEP 28: register the task status listener here (lessons/presentation-3hr/walkthrough.md)
    }

    public void route(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        Object object = deserializer.deserialize(message);
        switch (object) {
            case JsonRpcRequest request -> process(request);
            case JsonRpcNotification notification -> process(notification);
            case JsonRpcResponse successResponse -> process(successResponse);
            case JsonRpcErrorResponse errorResponse -> process(errorResponse);
            default -> logger.log("[API][RECEIVED] unknown message type: " + object);
        }
    }

    private void success(Long id, Object message) {
        JsonRpcResponse response = new JsonRpcResponse(JSON_RPC_VERSION, id, message);
        io.emit(response);
    }

    private void error(Long id, int code, String message) {
        JsonRpcErrorResponse response = new JsonRpcErrorResponse(
                JSON_RPC_VERSION, id, new JsonRpcError(code, message, null));
        io.emit(response);
    }

    private void process(JsonRpcRequest message) {
        UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());
        switch (uniqueKey) {
            // >>> STEP 6: paste the INITIALIZE handler here (lessons/presentation-3hr/walkthrough.md)
            //             (replaced by STEP 19, then by STEP 29)
            // >>> STEP 16: paste the PROMPTS_LIST handler here (lessons/presentation-3hr/walkthrough.md)
            // >>> STEP 17: paste the PROMPTS_GET handler here (lessons/presentation-3hr/walkthrough.md)
            // >>> STEP 14: paste the TOOLS_LIST handler here (lessons/presentation-3hr/walkthrough.md)
            //              (replaced by STEP 24, then by STEP 30)
            // >>> STEP 15: paste the TOOLS_CALL handler here (lessons/presentation-3hr/walkthrough.md)
            //              (replaced by STEP 21)
            // >>> STEP 32: paste the TASKS_GET and TASKS_RESULT handlers here (lessons/presentation-3hr/walkthrough.md)
            // >>> STEP 33: paste the TASKS_LIST and TASKS_CANCEL handlers here (lessons/presentation-3hr/walkthrough.md)
            // >>> STEP 11: paste the RESOURCES_LIST handler here (lessons/presentation-3hr/walkthrough.md)
            //              (replaced by STEP 25)
            // >>> STEP 12: paste the RESOURCES_READ handler here (lessons/presentation-3hr/walkthrough.md)
            //              (replaced by STEP 26)
            // >>> STEP 7: paste the PING handler here (lessons/presentation-3hr/walkthrough.md)
            // >>> STEP 18: paste the COMPLETION_COMPLETE handler here (lessons/presentation-3hr/walkthrough.md)
            // >>> STEP 22: paste the ELICITATION_CREATE_MESSAGE handler here (lessons/presentation-3hr/walkthrough.md)
            default -> logger.log("[API][RECEIVED] unhandled RpcRequest method: " + uniqueKey + " for message: " + message);
        }
    }

    private void process(JsonRpcNotification message) {
        UniqueKeys uniqueKey = UniqueKeys.fromValue(message.method());
        switch (uniqueKey) {
            // >>> STEP 8: paste the NOTIFICATIONS_INITIALIZED and NOTIFICATIONS_ROOTS_LIST_CHANGED handlers here (lessons/presentation-3hr/walkthrough.md)
            // >>> STEP 9: paste the NOTIFICATION_CANCELLED handler here (lessons/presentation-3hr/walkthrough.md)
            default -> logger.log("[API][RECEIVED] unhandled notification method: " + uniqueKey + " for message: " + message);
        }
    }

    private void process(JsonRpcResponse message) {
        // >>> STEP 8: paste the roots/list response handling here (lessons/presentation-3hr/walkthrough.md)
        // >>> STEP 22: paste the elicitation response handling (else-if branch) here (lessons/presentation-3hr/walkthrough.md)
    }

    // >>> STEP 21: paste the executeKeyWordSearchCall(...) method here (lessons/presentation-3hr/walkthrough.md)
    //              (replaced by STEP 31)

    private void process(JsonRpcErrorResponse message) {
        logger.log("[API][RECEIVED] error from client: " + message);
    }

    // >>> STEP 7: paste the sendSamplingMessage(...) method here (lessons/presentation-3hr/walkthrough.md)

    // >>> STEP 20: paste the sendElicitationMessage() method here (lessons/presentation-3hr/walkthrough.md)

    // >>> STEP 28: paste the relatedTaskMeta(...) helper here (lessons/presentation-3hr/walkthrough.md)

    // >>> STEP 31: paste the runToolAsTask(...) method here (lessons/presentation-3hr/walkthrough.md)

    // >>> STEP 32: paste the emitTaskResult(...) method here (lessons/presentation-3hr/walkthrough.md)

    // >>> STEP 28: paste the sendTaskStatusNotification(...) method here (lessons/presentation-3hr/walkthrough.md)

}
