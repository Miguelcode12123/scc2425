package main.java.tukano.impl.rest;

import jakarta.inject.Singleton;
import main.java.tukano.api.Blobs;
import main.java.tukano.api.rest.RestBlobs;
import main.java.tukano.impl.JavaBlobs;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * This class provides the REST endpoints for managing blobs (files) through HTTP.
 */
@Singleton
public class RestBlobsResource extends RestResource implements RestBlobs {

	final Blobs impl; // The implementation of the Blobs interface to interact with actual blob storage

	/**
	 * Constructor to initialize the RestBlobsResource class.
	 * It uses the JavaBlobs class as the actual implementation for the blob operations.
	 */
	public RestBlobsResource() {
		this.impl = JavaBlobs.getInstance();
	}

	/**
	 * Uploads a blob to the server.
	 *
	 * @param blobId The unique identifier for the blob.
	 * @param bytes The binary content of the blob to be uploaded.
	 * @param token The token required for authentication.
	 */
	@Override
	public void upload(String blobId, byte[] bytes, String token) {
		super.resultOrThrow( impl.upload(blobId, bytes, token));
	}

	/**
	 * Downloads a blob from the server.
	 *
	 * @param blobId The unique identifier for the blob.
	 * @param token The token required for authentication.
	 * @return The binary content of the downloaded blob.
	 */
	@Override
	public byte[] download(String blobId, String token) {
		triggerViewIncrement(blobId);
		return super.resultOrThrow( impl.download( blobId, token ));
	}

	/**
	 * Deletes a specific blob from the server.
	 *
	 * @param blobId The unique identifier for the blob to be deleted.
	 * @param token The token required for authentication.
	 */
	@Override
	public void delete(String blobId, String token) {
		super.resultOrThrow( impl.delete( blobId, token ));
	}

	/**
	 * Deletes all blobs associated with a user.
	 *
	 * @param userId The user identifier whose blobs will be deleted.
	 * @param password The password for authentication.
	 */
	@Override
	public void deleteAllBlobs(String userId, String password) {
		super.resultOrThrow( impl.deleteAllBlobs( userId, password ));
	}

	/**
	 * This method is called whenever a blob is downloaded, to trigger an increment in its view count.
	 *
	 * @param blobId The unique identifier of the blob whose view count is to be incremented.
	 */
	private void triggerViewIncrement(String blobId) {
		String incrementViewsUrl = "https://fun70663westeurope.azurewebsites.net/api/tukano/rest/blobs/" + blobId + "/incrementViews";

		try {
			// Creates a connection to the external service (Azure Function)
			HttpURLConnection connection = (HttpURLConnection) new URL(incrementViewsUrl).openConnection();
			connection.setRequestMethod("POST"); // Set the HTTP method to POST
			connection.getResponseCode(); // Trigger the function
			connection.disconnect(); // Disconnect after the request
		} catch (Exception e) {
			e.printStackTrace(); // Log any issues for debugging
		}
	}
}
