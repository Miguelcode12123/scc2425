package main.java.tukano.api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Represents a Blob (binary large object) that can be uploaded by a user.
 *
 * A Blob has a unique blobId, which is used to identify it.
 * It includes the binary content as well as metadata necessary for managing the blob.
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

    // Getters and Setters
    public String getBlobId() {
        return blobId;
    }

    public void setBlobId(String blobId) {
        this.blobId = blobId;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    // Method to increment view count
    public void incrementViewCount() {
        this.viewCount++;
    }

    @Override
    public String toString() {
        return "Blob [blobId=" + blobId + ", createdTimestamp=" + createdTimestamp + ", viewCount=" + viewCount + "]";
    }
}