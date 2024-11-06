package main.java.tukano.api.functions;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import main.java.tukano.api.User;
import main.java.tukano.api.UserCosmos;
import main.java.utils.CosmosDB;

import java.util.Optional;

/**
 * Azure Function that handles the creation of a new user.
 * This function is triggered by an HTTP POST request, and it attempts to insert the user data into the CosmosDB.
 * The function expects the user data to be provided in the request body.
 */
public class CreateUserFunction {
    private static final String HTTP_TRIGGER_NAME="req";
    private static final String HTTP_FUNCTION_NAME="CreateUser";
    private static final String HTTP_TRIGGER_ROUTE="tukano/rest/users";

    @FunctionName(HTTP_FUNCTION_NAME)
    public HttpResponseMessage createUser(
            @HttpTrigger(
                    name = HTTP_TRIGGER_NAME,
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = HTTP_TRIGGER_ROUTE)
            HttpRequestMessage<Optional<User>> request,
            final ExecutionContext context) {

        context.getLogger().info("Creating a new user.");

        // Check if the request body contains user data.
        if (request.getBody().isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("User data is missing.")
                    .build();
        }

        // Extract user data from the request body
        User user = request.getBody().get();
        UserCosmos uCosmos = new UserCosmos(user);

        // Attempt to insert the user data into CosmosDB.
        boolean success = CosmosDB.insertOne(uCosmos).isOK();
        // Some stats:
        // 4.21sec north central us

        if (success) {
            // If insertion is successful, return a 201 Created response.
            return request.createResponseBuilder(HttpStatus.CREATED)
                    .body("User created successfully.")
                    .build();
        } else {
            // If insertion fails, return a 500 Internal Server Error response.
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating user.")
                    .build();
        }
    }
}