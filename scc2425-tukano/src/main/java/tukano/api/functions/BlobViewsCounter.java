package main.java.tukano.api.functions;

import com.fasterxml.jackson.databind.JsonNode;
import main.java.tukano.api.Blob;
import main.java.tukano.api.Result;
import main.java.utils.CosmosDB;


public class BlobViewsCounter {

    /**
     * Increments the view count for a specific blob identified by its blobId.
     *
     * @param blobId The identifier of the blob whose view count will be incremented.
     * @return true if the view count was successfully incremented; false otherwise.
     */
    public static boolean incrementViewCount(String blobId) {
        try {
            // Fetch the current blob data using its ID
            Result<Blob> result = CosmosDB.getOne(blobId, Blob.class);
            if (result == null || !result.isOK()) {
                return false; // Blob not found or error in retrieval
            }

            Blob blob = result.value(); // Get the blob instance
            blob.incrementViewCount(); // Increment the view count using the method in Blob class

            Result<Blob> updateResult = CosmosDB.updateOne(blob);
            // Update the blob in CosmosDB
            return updateResult.isOK(); // Update the blob in the database and return the result
        } catch (Exception e) {
            e.printStackTrace(); // Log the exception for debugging
            return false; // Return false if any exception occurs
        }
    }
}