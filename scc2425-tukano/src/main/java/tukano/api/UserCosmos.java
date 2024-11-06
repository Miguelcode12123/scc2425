package main.java.tukano.api;

import jakarta.persistence.Entity;

/**
 * Represents a UserCosmos entity that extends the User class for Cosmos DB integration.
 */
@Entity
public class UserCosmos extends User {
    private String id; // This will be the id field for Cosmos DB

    /**
     * Default constructor for the UserCosmos class.
     * It calls the default constructor of the superclass (User).
     */
    public UserCosmos() {
        super();
    }

    /**
     * Constructor to initialize a UserCosmos object from an existing User object.
     *
     * @param user The User object that will be used to initialize the UserCosmos object
     */
    public UserCosmos(User user) {
        super(user.userId(), user.pwd(), user.email(), user.displayName());
        this.id = user.userId(); // Set the id as userId
    }

    /**
     * Gets the id for the Cosmos DB entity.
     * This field uniquely identifies the UserCosmos entity in Cosmos DB.
     *
     * @return The id used in Cosmos DB for this UserCosmos entity
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
     * Provides a string representation of the UserCosmos object.
     *
     * @return A string summarizing the UserCosmos object with relevant fields such as userId, password, email, and displayName
     */
    @Override
    public String toString() {
        return "UserWithId [userId=" + getUserId() + ", pwd=" + getPwd() +
                ", email=" + getEmail() + ", displayName=" + getDisplayName() + "]";
    }
}
