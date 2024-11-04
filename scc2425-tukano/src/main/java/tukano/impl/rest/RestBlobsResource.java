package main.java.tukano.impl.rest;

import jakarta.inject.Singleton;
import main.java.tukano.api.Blobs;
import main.java.tukano.api.rest.RestBlobs;
import main.java.tukano.impl.JavaBlobs;

import java.net.HttpURLConnection;
import java.net.URL;

@Singleton
public class RestBlobsResource extends RestResource implements RestBlobs {

	final Blobs impl;
	
	public RestBlobsResource() {
		this.impl = JavaBlobs.getInstance();
	}
	
	@Override
	public void upload(String blobId, byte[] bytes, String token) {
		super.resultOrThrow( impl.upload(blobId, bytes, token));
	}

	@Override
	public byte[] download(String blobId, String token) {
		triggerViewIncrement(blobId);
		return super.resultOrThrow( impl.download( blobId, token ));
	}

	@Override
	public void delete(String blobId, String token) {
		super.resultOrThrow( impl.delete( blobId, token ));
	}
	
	@Override
	public void deleteAllBlobs(String userId, String password) {
		super.resultOrThrow( impl.deleteAllBlobs( userId, password ));
	}

	private void triggerViewIncrement(String blobId) {
		String incrementViewsUrl = "https://fun70663westeurope.azurewebsites.net/api/tukano/rest/blobs/" + blobId + "/incrementViews";

		try {
			HttpURLConnection connection = (HttpURLConnection) new URL(incrementViewsUrl).openConnection();
			connection.setRequestMethod("POST");
			connection.getResponseCode(); // Trigger the function
			connection.disconnect();
		} catch (Exception e) {
			e.printStackTrace(); // Log any issues for debugging
		}
	}
}
