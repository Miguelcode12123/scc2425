package main.java.tukano.api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Represents a Blob (binary large object) that can be uploaded by a user.
 *
 * Each blob is uniquely identified by its blobId. This class provides methods for accessing and modifying
 * the blob's properties, including incrementing the view count.
 */
@Entity
public class Blob {

    @Id
    private String blobId; // Unique identifier for the blob
    private byte[] content; // The actual binary content of the blob
    private long createdTimestamp; // Timestamp indicating when the blob was created
    private int viewCount; // Count of how many times the blob has been viewed

    // Default constructor
    public Blob() {}

    // Constructor for initializing all attributes
    public Blob(String blobId, byte[] content, long createdTimestamp, int viewCount) {
        this.blobId = blobId;
        this.content = content;
        this.createdTimestamp = createdTimestamp;
        this.viewCount = viewCount;
    }

    // Constructor with defaults for view count and timestamp
    public Blob(String blobId, byte[] content) {
        this(blobId, content, System.currentTimeMillis(), 0);
    }

    // Getters and Setters for each attribute

    /**
     * Gets the blobId of the Blob
     *
     * @return the unique identifier of the blob
     */
    public String getBlobId() {
        return blobId;
    }

    /**
     * Sets the blobId of the Blob
     *
     * @param blobId The new blobId to set
     */
    public void setBlobId(String blobId) {
        this.blobId = blobId;
    }

    /**
     * Gets the content of the blob.
     *
     * @return A byte array representing the binary content of the blob
     */
    public byte[] getContent() {
        return content;
    }
    /**
     * Sets the content of the blob.
     *
     * @param content The binary data to set as the content of the blob
     */
    public void setContent(byte[] content) {
        this.content = content;
    }

    /**
     * Gets the creation timestamp of the blob.
     *
     * @return The timestamp when the blob was created
     */
    public long getCreatedTimestamp() {
        return createdTimestamp;
    }
    /**
     * Sets the creation timestamp of the blob.
     *
     * @param createdTimestamp The timestamp to set as the creation time of the blob
     */
    public void setCreatedTimestamp(long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }
    /**
     * Gets the current view count of the blob.
     *
     * @return The number of times the blob has been viewed
     */
    public int getViewCount() {
        return viewCount;
    }
    /**
     * Sets the view count of the blob.
     *
     * @param viewCount The new view count to set
     */
    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }
    // Method to increment view count
    /**
     * Increments the view count by one each time the method is called.
     * This method is useful for tracking how many times a blob has been accessed or viewed.
     */
    public void incrementViewCount() {
        this.viewCount++; // Increments the view count by 1
    }

    /**
     * Provides a string representation of the Blob object.
     * This method is useful for debugging and logging purposes, as it returns a concise summary of the blob's key attributes.
     *
     * @return A string representing the Blob object in a readable format
     */
    @Override
    public String toString() {
        return "Blob [blobId=" + blobId + ", createdTimestamp=" + createdTimestamp + ", viewCount=" + viewCount + "]";
    }
}