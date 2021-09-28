package movie;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;
import java.lang.SecurityManager;

// Send request to front end
// Display results to user

public class Client {

    private Client() {}

    

    public static void main(String[] args) {
    	if (System.getSecurityManager() == null) {
			System.setSecurityManager ( new SecurityManager() );
    	}

		String host = (args.length < 1) ? null : args[0];
		Scanner scanner = new Scanner(System.in);
		try {
		    Registry registry = LocateRegistry.getRegistry("localhost", 37001);
			FrontEndInterface stub = (FrontEndInterface) registry.lookup("FrontEnd");

		    String response;
		    String out;
		    boolean running = true;
		    if (stub == null) {
		    	System.out.println("Can't connect to front end server. Please check the front end is active.");
		    	return;
		    }

		    System.out.println("Enter a username: ");
		    String userId = scanner.nextLine();

		    while (running) {
		    	String[] input = new String[3];
		    	System.out.println("Enter S to start a search, R to rate a movie, or Q to quit.");
		    	String query = scanner.nextLine();

		    	if (query.toLowerCase().equals("s")) {
		    		input[0] = "title";
		    		System.out.println("Enter your search: ");
		    		input[1] = scanner.nextLine();

		    	} else if (query.toLowerCase().equals("r")) {
		    		input[0] = "id";
		    		System.out.println("Enter the Movie ID, or \"b\" to return to the menu: ");
		    		String movieId = scanner.nextLine();
		    		if (!movieId.equals("b")) {
		    			input[1] = movieId;
			    		input[2] = userId;
				    	response = stub.request(input);
				    	if (response != null) {
			    			System.out.println("Movie Title: " + response);
			    			input[0] = movieId;
			    			System.out.println("Enter your rating (between 0-5), or \"b\" to return to the menu: ");
			    			input[1] = scanner.nextLine();
			    			if (input[1].equals("b")) {
			    				input = new String[1];
			    				System.out.println();
			    			}
			    		} else {
			    			System.out.println("Invalid Movie ID. Please double check your input.");
			    		}
		    		} else {
		    			response = null;
		    		}

		    	} else if (query.toLowerCase().equals("q")) {
		    		running = false;
		    	} else {
		    		System.out.println(query + " is not a valid input. Please try again.");
		    	}


			    if (stub != null && input.length > 1) {
			    	input[2] = userId;
			    	response = stub.request(input);
			    	if (response != null) {
			    		System.out.println(response);
			    	}
			    	System.out.println();
			    } else if (stub == null) {
			    	System.out.println("No Servers are currently available. Please try again later.");
			    	running = false;
			    }
			}


		} catch (Exception e) {
			System.err.println("Client exception: " + e.toString());
			e.printStackTrace();
		}
    }
}
