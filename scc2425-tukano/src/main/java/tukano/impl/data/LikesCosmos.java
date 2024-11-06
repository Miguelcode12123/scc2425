package main.java.tukano.impl.data;

import jakarta.persistence.Entity;

/**
 * Represents a Likes entity in Cosmos DB, extending the basic Likes class.
 */
@Entity
public class LikesCosmos extends Likes{

    private String id; // The id field used in Cosmos DB to uniquely identify the like relationship

    /**
     * Default constructor for the LikesCosmos class.
     */
    public LikesCosmos() {
        super();
    }

    /**
     * Constructor to initialize a LikesCosmos object from an existing Likes object.
     *
     * @param l The Likes object that will be used to initialize the LikesCosmos object
     */
    public LikesCosmos( Likes l) {
        super(l.userId, l.shortId, l.ownerId);
        this.id = l.userId + l.shortId;
    }

    /**
     * Gets the id for the Cosmos DB entity.
     * This id uniquely identifies the LikesCosmos relationship in Cosmos DB.
     *
     * @return The id used in Cosmos DB for the LikesCosmos entity
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the id for the Cosmos DB entity.
     *
     * @param id The id to set for the Cosmos DB entity
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Provides a string representation of the LikesCosmos object.
     *
     * @return A string summarizing the LikesCosmos object with relevant fields such as userId, shortId, and ownerId
     */
    @Override
    public String toString() {
        return "LikesCosmos [userId=" + getUserId() + ", shortId=" + getShortId() + ", ownerId=" + getOwnerId() + "]";
    }
}
