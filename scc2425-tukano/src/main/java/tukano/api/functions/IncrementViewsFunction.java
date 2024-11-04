package main.java.tukano.api.functions;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;


import java.util.Optional;

public class IncrementViewsFunction {
    private static final String HTTP_TRIGGER_NAME = "req";
    private static final String HTTP_FUNCTION_NAME = "BlobViewCounter";
    private static final String HTTP_TRIGGER_ROUTE = "tukano/rest/blobs/{blobId}/incrementViews";

    @FunctionName(HTTP_FUNCTION_NAME)
    public HttpResponseMessage incrementViews(
            @HttpTrigger(
                    name = HTTP_TRIGGER_NAME,
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = HTTP_TRIGGER_ROUTE)
            HttpRequestMessage<Optional<String>> request,
            @com.microsoft.azure.functions.annotation.BindingName("blobId") String blobId,
            final ExecutionContext context) {

        context.getLogger().info("Incrementing view count for blob: " + blobId);

        // Check if blobId is provided
        if (blobId == null || blobId.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Blob ID is missing.")
                    .build();
        }

        // Increment views in CosmosDB
        boolean success = BlobViewsCounter.incrementViewCount(blobId);

        if (success) {
            return request.createResponseBuilder(HttpStatus.OK)
                    .body("View count incremented successfully.")
                    .build();
        } else {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error incrementing view count.")
                    .build();
        }
    }
}