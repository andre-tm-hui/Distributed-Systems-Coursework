package movie;
	
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.lang.SecurityManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;

// Receive query from client
// Send query and last time stamp (a, b, c) to replica(s) 
// Receive result from replica and new time stamp from replica
// Return result to client

public class FrontEnd implements FrontEndInterface {

	public static ArrayList<Integer> timestamp = new ArrayList<Integer>();
	public static ArrayList<ReplicaInterface> replicas = new ArrayList<ReplicaInterface>();
	public static int port = 37001;
	public static int numOfReplicas = -1;

	public static void main(String args[]) {
		if (System.getSecurityManager() == null) {
			System.setSecurityManager ( new SecurityManager() );
    	}
		
		if (args.length == 0) {
			System.out.println("Please specify the number of replicas.");
			return;
		}
		try {
			numOfReplicas = Integer.parseInt(args[0]);
		} catch (Exception e) {
			System.out.println("Please specify a number.");
		}
		if (numOfReplicas < 3) {
			System.out.println("Please specify a larger number of servers.");
		}
		for (int i = 0; i < numOfReplicas; i++) {
			timestamp.add(0);
		}

		try {
    		FrontEnd obj = new FrontEnd();
    		FrontEndInterface stub = (FrontEndInterface) UnicastRemoteObject.exportObject(obj, 0);
    		Registry registry = LocateRegistry.getRegistry("127.0.0.1", port);
    		registry.rebind("FrontEnd", stub);
    		System.err.println("Server bound to registry name FrontEnd on port " + Integer.toString(port));
    	} catch (Exception e) {
    		System.err.println("Server exception: " + e.toString());
	    	e.printStackTrace();
    	}
    }

   	// Function to populate arraylist of replica servers
    public void getReplicas() {
    	replicas.clear();
    	try {
	    	Registry registry = LocateRegistry.getRegistry("127.0.0.1", port);
	    	for (int i = 0; i < numOfReplicas; i++) {
				ReplicaInterface replica = (ReplicaInterface) registry.lookup("Replica" + Integer.toString(i));
				replicas.add(replica);
	    	}
	    } catch (Exception e) {
	    	System.out.println("Failed to get Replicas");
	    }
    }

    public String request(String[] args) {
    	getReplicas();
    	try {
    		ReplicaInterface stub = null;
	    	Map<ArrayList<Integer>, String> resp = null;
	    	// Randomly select a server
	    	Random r = new Random();
	    	int i = r.nextInt(replicas.size());
	    	ArrayList<Integer> is = new ArrayList<Integer>();

	    	// Select/check for server availability
	    	for (int j = 0; j < replicas.size(); j++) {
	    		// Add index i to the list of checked servers
	    		is.add(i);
	    		try {
	    			// Do not connect to a server if it's status is not ready
	    			if(!replicas.get(i).status().equals("ready")) {
	    				System.out.println("Replica" + Integer.toString(i) + " is " + replicas.get(i).status() + ".");
		    			while (is.contains(i) && is.size() < numOfReplicas) {
		    				i = r.nextInt(replicas.size());
		    			}
		    		} else {
		    			stub = replicas.get(i);
		    			j += replicas.size();
		    		}
	    		} catch (Exception e) {
	    			System.out.println("Replica" + Integer.toString(i) + " is offline.");
	    			while (is.contains(i) && is.size() < numOfReplicas) {
	    				i = r.nextInt(replicas.size());
	    			}
	    		}
	    	}

	    	// stub is null if no servers are available
	    	if (stub != null){
	    		// Check for validity of the args, and identify the type of request
	    		if (args.length == 3) {
	    			if (args[0].equals("title") || args[0].equals("id")) {
	    				resp = stub.query(timestamp, args);
	    			} else {
	    				resp = stub.update(timestamp, args, true);
	    			}
	    		} else {
	    			return "Your submission was unsuccessful: Command was not recognised.";
	    		}

		    	for (ArrayList<Integer> k : resp.keySet()) {
		    		timestamp = k;
		    		System.out.println("Query processed at Replica" + Integer.toString(i) + ", timestamp: " + getTimestamp());
		    		return resp.get(k);
		    	}
		    	return "Error";
		    } else {
		    	return "Servers are currently unavailable. Please try again later.";
		    }
	    } catch (Exception e) {
	    	return null;
	    }
	}

	public String getTimestamp() {
		String tsString = "";
		for (int i = 0; i < timestamp.size(); i++) {
			tsString += Integer.toString(timestamp.get(i)) + ",";
		}
		return tsString.substring(0, tsString.length()-1);
	}
	

}