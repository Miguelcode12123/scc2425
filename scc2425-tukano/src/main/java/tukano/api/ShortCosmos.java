package main.java.tukano.api;

import jakarta.persistence.Entity;
import main.java.tukano.impl.Token;

/**
 * Represents a ShortCosmos, which extends the functionality of the Short class for Cosmos DB integration.
 * This class is used to manage Short objects in the Cosmos DB and adds Cosmos DB-specific fields and behaviors.
 */
@Entity
public class ShortCosmos extends Short {

    private String id; // This will be the id field for Cosmos DB

    // Default constructor for the ShortCosmos class.
    public ShortCosmos() {
        super(); // Call the superclass constructor
    }

    /**
     * Constructor to initialize the ShortCosmos object with specific values.
     *
     * @param shortId The unique identifier for the short object
     * @param ownerId The identifier for the owner of the short object
     * @param blobUrl The URL pointing to the content of the short object
     * @param timestamp The timestamp when the short was created
     * @param totalLikes The number of likes the short has received
     */
    public ShortCosmos(String shortId, String ownerId, String blobUrl, long timestamp, int totalLikes) {
        super(shortId, ownerId, blobUrl, timestamp, totalLikes); // Call the superclass constructor
        this.id = shortId;
    }

    /**
     * Constructor to create a ShortCosmos object from an existing Short object.
     *
     * @param shrt A Short object to initialize the ShortCosmos object
     */
    public ShortCosmos(Short shrt) {
        super(shrt.shortId, shrt.ownerId, shrt.blobUrl, shrt.timestamp, shrt.totalLikes);
        this.id = shrt.shortId; // Set the id as shortId
    }

    /**
     * Gets the id for the Cosmos DB entity.
     *
     * @return The id used in Cosmos DB for the ShortCosmos entity
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the id for the Cosmos DB entity.
     *
     * @param id The id to set for Cosmos DB
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Provides a string representation of the ShortCosmos object.
     *
     * @return A string summarizing the ShortCosmos object with relevant fields
     */
    @Override
    public String toString() {
        return "ShortCosmos [shortId=" + getShortId() + ", ownerId=" + getOwnerId() +
                ", blobUrl=" + getBlobUrl() + ", timestamp=" + getTimestamp() +
                ", totalLikes=" + getTotalLikes() + "]";
    }

    /**
     * Creates a copy of the ShortCosmos object with updated like count and a token appended to the blob URL.
     *
     * @param totLikes The updated total number of likes for the short
     * @return A new ShortCosmos object with updated like count and URL containing a token
     */
    public ShortCosmos copyWithLikesAndToken(long totLikes) {
        var urlWithToken = String.format("%s?token=%s", getBlobUrl(), Token.get(getBlobUrl()));
        return new ShortCosmos(getShortId(), getOwnerId(), urlWithToken, getTimestamp(), (int) totLikes);
    }
}
