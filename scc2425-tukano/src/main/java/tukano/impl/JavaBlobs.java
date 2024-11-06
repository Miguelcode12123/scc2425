package main.java.tukano.impl;

import static java.lang.String.format;
import static main.java.tukano.api.Result.error;
import static main.java.tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.function.Consumer;
import java.util.logging.Logger;

import main.java.tukano.api.Blobs;
import main.java.tukano.api.Result;
import main.java.tukano.impl.rest.TukanoRestServer;
import main.java.tukano.impl.storage.BlobStorage;
import main.java.tukano.impl.storage.BlobSystemStorage;
import main.java.utils.Hash;
import main.java.utils.Hex;
import main.java.utils.RedisCache; // Import for the Cache
import redis.clients.jedis.Jedis;

public class JavaBlobs implements Blobs {

	// Singleton instance to ensure only one object of JavaBlobs exists
	private static Blobs instance;

	// Logger for logging events in this class
	private static Logger Log = Logger.getLogger(JavaBlobs.class.getName());

	// Base URI for the server that handles blobs
	public String baseURI;

	// Object to interact with the blob storage system
	private BlobSystemStorage storage;

	// Method to get the singleton instance of JavaBlobs
	synchronized public static Blobs getInstance() {
		if( instance == null )
			instance = new JavaBlobs();
		return instance;
	}

	// Constructor to initialize the storage and baseURI
	private JavaBlobs() {
		storage = new BlobSystemStorage();
		baseURI = String.format("%s/%s/", TukanoRestServer.serverURI, Blobs.NAME);
	}

	/**
	 * Method to upload a blob (file) to the storage system.
	 * It also caches the blob after upload.
	 * @param blobId - ID of the blob to be uploaded
	 * @param bytes - Byte array representing the blob data
	 * @param token - The authorization token for validation
	 * @return A Result object indicating success or failure of the upload
	 */
	@Override
	public Result<Void> upload(String blobId, byte[] bytes, String token) {
		// Logging the upload action with blobId and hash of the blob (for tracking)
		Log.info(() -> format("upload : blobId = %s, sha256 = %s, token = %s\n", blobId, Hex.of(Hash.sha256(bytes)), token));

		// Check if the blobId is valid and the token matches
		if (!validBlobId(blobId, token))
			return error(FORBIDDEN);

		// Clear the cache after the upload by storing the blobId with its hash in Redis
		Result<Void> result = storage.write(toPath(blobId), bytes);
		Log.info("Upload Path \n");
		Log.info(toPath(blobId));

		// Clear cache on upload
		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			jedis.set("blob:" + blobId, Hex.of(Hash.sha256(bytes)));
			jedis.expire("blob:" + blobId, 3600);
		}

		return result;
	}

	/**
	 * Method to download a blob (file) from the storage system.
	 * If the blob is in the cache, it retrieves it from there.
	 * @param blobId - ID of the blob to be downloaded
	 * @param token - The authorization token for validation
	 * @return A Result object containing the blob data in byte array or an error
	 */
	@Override
	public Result<byte[]> download(String blobId, String token) {
		// Logging the download action with blobId
		Log.info(() -> format("download : blobId = %s, token=%s\n", blobId, token));

		// Check if the blobId is valid and the token matches
		if( !validBlobId(blobId, token) )
			return error(FORBIDDEN);

		// Try to fetch the blob data from the cache (Redis)
		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			String cachedData = jedis.get("blob:" + blobId);
			if (cachedData != null) {
				return Result.ok(cachedData.getBytes());
			}
		}
		// If blob is not in the cache, fetch it from the storage
		return storage.read( toPath( blobId ) );
	}

	/**
	 * Method to download a blob to a sink (callback function for processing the blob).
	 * @param blobId - ID of the blob to be downloaded
	 * @param sink - A Consumer function that processes the blob data
	 * @param token - The authorization token for validation
	 * @return A Result object indicating success or failure of the download
	 */
	@Override
	public Result<Void> downloadToSink(String blobId, Consumer<byte[]> sink, String token) {
		// Logging the download to sink action with blobId
		Log.info(() -> format("downloadToSink : blobId = %s, token = %s\n", blobId, token));

		// Check if the blobId is valid and the token matches
		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		// Read the blob data and send it to the sink function for processing
		return storage.read( toPath(blobId), sink);
	}

	/**
	 * Method to delete a blob from the storage system.
	 * It also clears the blob from the cache after deletion.
	 * @param blobId - ID of the blob to be deleted
	 * @param token - The authorization token for validation
	 * @return A Result object indicating success or failure of the delete operation
	 */
	@Override
	public Result<Void> delete(String blobId, String token) {
		// Logging the delete action with blobId
		Log.info(() -> format("delete : blobId = %s, token=%s\n", blobId, token));

		// Check if the blobId is valid and the token matches
		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		// Clear the blob from the cache in Redis
		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			jedis.del("blob:" + blobId);
		}

		Log.info("Delete Path \n");
		Log.info(toPath(blobId));

		// Delete the blob from the storage
		return storage.delete( toPath(blobId));
	}

	/**
	 * Method to delete all blobs for a specific user from the storage.
	 * @param userId - ID of the user whose blobs need to be deleted
	 * @param token - The authorization token for validation
	 * @return A Result object indicating success or failure of the delete operation
	 */
	@Override
	public Result<Void> deleteAllBlobs(String userId, String token) {
		// Logging the delete all blobs action with userId
		Log.info(() -> format("deleteAllBlobs : userId = %s, token=%s\n", userId, token));

		// Validate if the token is correct for the given userId
		if( ! Token.isValid( token, userId ) )
			return error(FORBIDDEN);

		// Delete all blobs associated with the user's ID
		return storage.deleteAllBlobsInPath(addPrefix(userId));
	}

	// Helper method to check if the blobId is valid
	private boolean validBlobId(String blobId, String token) {
		return Token.isValid(token, toURL(blobId));
	}

	// Helper method to convert the blobId to a valid storage path
	private String toPath(String blobId) {
		return blobId.replace("+", "/");
	}

	// Helper method to add the user's ID as a prefix for their blobs
	private String addPrefix(String userId) {
		return userId + "/";
	}

	// Helper method to convert the blobId to a valid URL
	private String toURL( String blobId ) {
		return baseURI + blobId ;
	}
}
