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

/**
 * Azure Function that increments the view count for a specific blob identified by its blobId.
 * This function is triggered by an HTTP POST request, where the blobId is provided as part of the URL.
 * It calls the BlobViewsCounter service to update the view count in the CosmosDB.
 * If the view count is successfully incremented, it returns an HTTP 200 OK response.
 */
public class IncrementViewsFunction {
    private static final String HTTP_TRIGGER_NAME = "req";
    private static final String HTTP_FUNCTION_NAME = "BlobCounter";
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

        // Check if the blobId parameter is provided in the route.
        if (blobId == null || blobId.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Blob ID is missing.")
                    .build();
        }

        // Call the incrementViewCount method to update the view count in CosmosDB.
        boolean success = BlobViewsCounter.incrementViewCount(blobId);

        if (success) {
            // If the update was successful, respond with HTTP 200 OK.
            return request.createResponseBuilder(HttpStatus.OK)
                    .body("View count incremented successfully.")
                    .build();
        } else {
            // If there was an error, respond with HTTP 500 Internal Server Error.
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error incrementing view count.")
                    .build();
        }
    }
}