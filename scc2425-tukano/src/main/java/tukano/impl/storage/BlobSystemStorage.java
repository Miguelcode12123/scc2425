package main.java.tukano.impl.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.BlobClient;  // For BlobClient
import java.io.ByteArrayInputStream;      // For ByteArrayInputStream
import java.util.function.Consumer;
import java.util.logging.Logger;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import main.java.tukano.api.Result;
import main.java.tukano.impl.JavaUsers;
import main.java.utils.Hash;

public class BlobSystemStorage implements BlobStorage {
    String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=st7066270663;AccountKey=BQp5y2hpOFd6oDJRIpt4C/i0hMnGnVDwTbqWqHg2gMFuInxxWkQ2YomHeGmIluf/ZA5v+2F4AMlM+AStg9AZyQ==;EndpointSuffix=core.windows.net";
    String containerName = "blobs";

    private BlobContainerClient containerClient;

    /**
     * Constructor for BlobSystemStorage.
     */
    public BlobSystemStorage() {
        // Initialize BlocContainerCLient
        containerClient = new BlobContainerClientBuilder()
                .connectionString(storageConnectionString)
                .containerName(containerName)
                .buildClient();
        Map<String, byte[]> map = new HashMap<>();
    }

    /**
     * Writes a blob to Azure Blob Storage.
     *
     * @param path The path (name) of the blob to be stored.
     * @param bytes The byte data of the blob.
     * @return A Result indicating whether the operation succeeded or failed.
     */
    @Override
    public Result<Void> write(String path, byte[] bytes) {
        BlobClient blobClient = containerClient.getBlobClient(path);
        blobClient.upload(new ByteArrayInputStream(bytes), bytes.length, true);
        return Result.ok();
    }

    /**
     * Deletes a specific blob from Azure Blob Storage.
     *
     * @param path The path (name) of the blob to be deleted.
     * @return A Result indicating whether the operation succeeded or failed.
     */
    @Override
    public Result<Void> delete(String path) {
        try {
            // Get the BlobClient for the specific blob
            BlobClient blobClient = containerClient.getBlobClient(path);

            // Check if the blob exists before trying to delete it
            if (blobClient.exists()) {
                // Delete the blob
                blobClient.delete();
                return Result.ok();  // Return success if deletion is successful
            } else {
                throw  new WebApplicationException(Status.NOT_FOUND);
            }
        } catch (Exception e) {
            System.err.println("EXCEPTION: " + e);
            throw  new WebApplicationException(Status.CONFLICT);
        }
    }

    /**
     * Deletes all blobs in a specific path (directory) in the Azure Blob Storage.
     *
     * @param path The directory path where blobs should be deleted.
     * @return A Result indicating whether the operation succeeded or failed.
     */
    public Result<Void> deleteAllBlobsInPath(String path) {
        try {
            // List all blobs in the given path (directory)
            for (BlobItem blobItem : containerClient.listBlobsByHierarchy(path)) {
                // Get the BlobClient for each blob in the directory
                BlobClient blobClient = containerClient.getBlobClient(blobItem.getName());

                // Delete each blob
                blobClient.delete();
            }
            // Return a successful result after deletion
            return Result.ok();
        } catch (Exception e) {
            System.err.println("EXCEPTION: " + e);
            throw new WebApplicationException(Status.CONFLICT);
        }
    }

    /**
     * Reads a blob's content from Azure Blob Storage.
     *
     * @param path The path (name) of the blob to read.
     * @return A Result containing the byte data of the blob.
     */
    @Override
    public Result<byte[]> read(String path) {
        return Result.ok(download(path));
    }


    /**
     * Reads a blob's content and sends it to the provided sink (consumer).
     *
     * @param path The path (name) of the blob to read.
     * @param sink A Consumer that processes the byte data of the blob.
     * @return A Result indicating whether the operation succeeded or failed.
     */
    @Override
    public Result<Void> read(String path, Consumer<byte[]> sink) {
        downloadSink(path, sink);
        return Result.ok();
    }

    /**
     * Downloads the byte content of a specific blob.
     *
     * @param id The path (name) of the blob to download.
     * @return The byte array content of the blob.
     */
    private byte[] download(String id) {
        try {
            return containerClient.getBlobClient(id).downloadContent().toBytes();  // Download blob from Azure
        } catch (Exception e) {
            throw new WebApplicationException(Status.NOT_FOUND);  // Handle if the blob does not exist
        }
    }

    /**
     * Downloads the byte content of a specific blob and processes it using the provided sink.
     *
     * @param id The path (name) of the blob to download.
     * @param sink A Consumer that processes the byte data of the blob.
     * @return The byte array content of the blob.
     */
    private byte[] downloadSink (String id, Consumer<byte[]> sink) {
        try {
            return containerClient.getBlobClient(id).downloadContent().toBytes();  // Download blob from Azure
        } catch (Exception e) {
            throw new WebApplicationException(Status.NOT_FOUND);  // Handle if the blob does not exist
        }
    }

    /**
     * Lists the names of all blobs in the container.
     *
     * @return A list of blob names as strings.
     */
    private List<String> list() {
        List<String> blobNames = new ArrayList<>();
        for (BlobItem blobItem : containerClient.listBlobs()) {
            blobNames.add(blobItem.getName());
        }
        return blobNames;
    }
}