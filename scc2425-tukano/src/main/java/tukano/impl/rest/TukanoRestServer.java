package main.java.tukano.impl.rest;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import main.java.tukano.impl.Token;
import main.java.utils.Args;
import main.java.utils.IP;
import main.java.utils.Props;

/**
 * The TukanoRestServer class initializes and configures the Tukano RESTful server.
 * This class is responsible for setting up the server URI, logging server readiness,
 * and adding REST resources (e.g., blobs, users, shorts) to the server.
 */
@ApplicationPath("/rest") // Defines the base URI path for the RESTful API
public class TukanoRestServer extends Application {
	final private static Logger Log = Logger.getLogger(TukanoRestServer.class.getName());
	static String SERVER_BASE_URI = "http://%s:%s/tukano/rest";
	public static final int PORT = 8080;
	public static String serverURI;
			
	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
		serverURI = String.format(SERVER_BASE_URI, IP.hostname(), PORT);
	}

	/**
	 * Constructor for the TukanoRestServer.
	 * It sets the secret key for the Token and logs the server's URI.
	 */
	public TukanoRestServer() {
		Token.setSecret("defaultSecret1234567890");
		Log.info(String.format("Tukano Server ready @ %s\n",  serverURI));
	}

	/**
	 * This method returns the singleton instances of the REST resource classes.
	 * These resources handle various endpoints related to blobs, users, and shorts.
	 *
	 * @return A set of singleton objects that represent the REST resources.
	 */
	@Override
	public Set<Object> getSingletons() {
		Set<Object> singletons = new HashSet<>();
		singletons.add(new RestBlobsResource()); // Handles blob-related API endpoints
		singletons.add(new RestUsersResource()); // Handles user-related API endpoints
		singletons.add(new RestShortsResource()); // Handles short-related API endpoints
		return singletons;
	}

	public static void main(String[] args) throws Exception {
		Args.use(args);
		Token.setSecret( Args.valueOf("-secret", "defaultSecret1234567890"));
		//Props.load( Args.valueOf("-props", "").split(","));
		Log.info("Server setup complete. Ready for deployment.");
	}

}
