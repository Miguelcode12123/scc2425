package main.java.tukano.impl.data;

import jakarta.persistence.Entity;

/**
 * Represents a Following entity in Cosmos DB, extending the basic Following class.
 */
@Entity
public class FollowingCosmos extends Following{
    private String id; // The id field used in Cosmos DB to uniquely identify the following relationship

    /**
     * Default constructor for the FollowingCosmos class.
     */
    public FollowingCosmos() {
        super();
    }

    /**
     * Constructor to initialize a FollowingCosmos object from an existing Following object.
     *
     * @param f The Following object that will be used to initialize the FollowingCosmos object
     */
    public FollowingCosmos(Following f) {
        super(f.follower, f.followee);
        this.id = f.follower+f.followee;
    }

    /**
     * Gets the id for the Cosmos DB entity.
     * This id uniquely identifies the FollowingCosmos relationship in Cosmos DB.
     *
     * @return The id used in Cosmos DB for the FollowingCosmos entity
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
     * Provides a string representation of the FollowingCosmos object.
     *
     * @return A string summarizing the FollowingCosmos object with relevant fields such as follower and followee
     */
    @Override
    public String toString() {
        return "FollowingCosmos [follower=" + getFollower() + ", followee=" + getFollowee() + "]";
    }
}
