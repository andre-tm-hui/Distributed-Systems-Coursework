package movie;
	
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.lang.SecurityManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import java.util.Arrays;
import java.util.Scanner;
import java.util.ArrayList;

// Receive request and time stamp from front end
// If request is an update, add to update queue of both other replica servers
// If client time stamp is older than local time stamp, process the query as normal
// Else wait until the local time stamp is caught up, ie. wait on updates from other servers
// 
// Gossip works by:
// 		- Randomly select another server at a fixed interval
// 		- Send list of all new updates made in the form of logs
// 		- For any update that was sent, clear the queued updates for that server
// 		- Recipient server processes queued updates to see which updates are new, and updates accordingly **** TODO: have recipient server be able to tell origin of certain updates 
// 		- Compare timestamps in logs - if a timestamp is missing from the log, add that update
// 		- Mainly look at the value for the server that just sent the update
// 		- Timestamp looks like (x, y, z), x for updates to server 1, y for updates to server 2, z for updates to server 3


public class Replica implements ReplicaInterface {

	private static String status;
	private static int port = 37001;
	// Logs stored as a Map of timestamps to arguments for the update
	private static Map<ArrayList<Integer>, String[]> logs = new HashMap<ArrayList<Integer>, String[]>();
	private static ArrayList<Integer> timestamp = new ArrayList<Integer>();
	private static int replicaNo = -1;
	private static boolean gossipFeed = false;
	// Replicas stored as a Map of their stubs to another Map containing a list of updates, in the form of timestamps mapped to the update arguments
	private static Map<ReplicaInterface, Map<ArrayList<Integer>, String[]>> replicas = new HashMap<ReplicaInterface, Map<ArrayList<Integer>, String[]>>();
	private static Map<ReplicaInterface, String> replicaRegistries = new HashMap<ReplicaInterface, String>();
	// Initialize the database
	private static Database db = new Database();
	private static int interval = 1000;

    public Replica() { }

    //
    // Startup Functions
	//
    public static void main(String args[]) {
    	if (System.getSecurityManager() == null) {
			System.setSecurityManager ( new SecurityManager() );
    	}

    	// Check the arg for the number of replicas to expect, and initialize timestamp if the number is at least 3.
    	int numOfReplicas = 0;
    	if (args.length != 0) {
    		try {
    			numOfReplicas = Integer.parseInt(args[0]);
    		} catch (Exception e) {
    			System.out.println("Please specify a number.");
    			return;
    		}
    	} else {
    		System.out.println("Please specify the number of servers.");
    		return;
    	}
    	if (numOfReplicas < 3) {
    		System.out.println("Please specify a larger number of servers.");
    		return;
    	}

    	for (int r = 0; r < numOfReplicas; r++) {
    		timestamp.add(0);
    	}

    	if (args.length == 2) {
    		try {
    			interval = Integer.parseInt(args[1]);
    		} catch (Exception e) {
    			System.out.println("Input for interval could not be parsed, using default of 1000ms.");
    		}
    	}


    	// Bind the replica to a registry on specified port
    	int i = 0;
    	boolean binded = false;
    	while (!binded && i < numOfReplicas) {
	    	try {
	    		Replica obj = new Replica();
	    		ReplicaInterface stub = (ReplicaInterface) UnicastRemoteObject.exportObject(obj, 0);
	    		Registry registry = LocateRegistry.getRegistry("127.0.0.1", port);
	    		registry.bind("Replica" + Integer.toString(i), stub);
	    		System.err.println("Server bound to registry name Replica" + Integer.toString(i) + " on port " + Integer.toString(port));
	    		status = "ready";
	    		binded = true;
	    		replicaNo = i;

	    	} catch (Exception e) {
	    		// Check if the registry name is in use or not
	    		try {
		    		Registry registry = LocateRegistry.getRegistry("127.0.0.1", port);
		    		ReplicaInterface stub = (ReplicaInterface) registry.lookup("Replica" + Integer.toString(i));
		    		try {
		    			// If status() returns something, the registry name is in use/bound to a replica
		    			stub.status();
		    			i++;
		    		} catch (Exception er) {
		    			// If status() throws an error, the registry name is set but not in use/bound, hence rebind at the server
		    			System.out.println("Rebinding at Replica" + Integer.toString(i));
		    			Replica obj = new Replica();
	    				stub = (ReplicaInterface) UnicastRemoteObject.exportObject(obj, 0);
		    			registry.rebind("Replica" + Integer.toString(i), stub);
		    			// Initial status is offline, as server needs to replicate another server to ensure it is up to date
		    			status = "offline";
		    			binded = true;
		    			replicaNo = i;
		    		}
		    	} catch (Exception err) {
		    		err.printStackTrace();
		    		return;
		    	}
	    	}
	    }


		// Check if the replica is actually bound
	    if (replicaNo == -1) {
	    	System.out.println("Not bound to a registry.");
	    	return;
	    }


	    // Add the stubs of the other replicas to an arraylist
	    for (int j = 0; j < numOfReplicas; j++) {
	    	if (j != i) {
	    		try {
	    			Registry registry = LocateRegistry.getRegistry("127.0.0.1", port);
		    		ReplicaInterface stub = (ReplicaInterface) registry.lookup("Replica" + Integer.toString(j));
		    		Map<ArrayList<Integer>, String[]> updatesToSend = new HashMap<ArrayList<Integer>, String[]>();
		    		replicas.put(stub, updatesToSend);
		    		replicaRegistries.put(stub, "Replica" + Integer.toString(j));
	    		} catch (Exception e) {
	    			// Do nothing - some latter servers may not have been initialized yet
	    		}
	    	}
	    }


	    // Call upon other previously initialized servers to add the newly instantiated server
	    for (ReplicaInterface stub : replicas.keySet()) {
	    	try {
	    		stub.addReplica(i);
	    	} catch (Exception e) {
	    		e.printStackTrace();
		    	return;
	    	}
	    	
	    }


	    // Set a fixed interval at which the replica gossips
		Timer timer = new Timer();
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				try {
					gossip();
				} catch (Exception e) {
					System.err.println("Client exception: " + e.toString());
				}
			}
		};
		timer.schedule(task, 0, interval); // Gossips every "interval" milliseconds


		// Create user interface for the server, to allow changing of states and other useful functions
		Scanner scanner = new Scanner(System.in);
		String input = "";
		System.out.println("To change states, enter \"ready\", \"overloaded\" or \"offline\".");
		System.out.println("To get the local timestamp, enter \"timestamp\".");
		System.out.println("To toggle live gossip feed, enter \"gossip\".");
		while (true) {
			input = scanner.nextLine();
			if (input.equals("ready")) {
				status = "ready";
				System.out.println("Server status set to ready.");
			} else if (input.equals("overloaded")) {
				status = "overloaded";
				System.out.println("Server status set to overloaded.");
			} else if (input.equals("offline")) {
				status = "offline";
				System.out.println("Server status set to offline.");
			} else if (input.equals("timestamp")) {
				System.out.println(getTimestamp());
			} else if (input.equals("gossip")) {
				gossipFeed = !gossipFeed;
			} else if (input.equals("replicate")) {
				System.out.println("Getting logs from another replica.");
				replicate();
			} else {
				System.out.println("Input not recognized.");
			}
		}
    }


    // Adds a replica to the arraylist of replicas, using i to identify the registry name
    public void addReplica(int i) {
    	try {
    		Registry registry = LocateRegistry.getRegistry("127.0.0.1", port);
    		ReplicaInterface stub = (ReplicaInterface) registry.lookup("Replica" + Integer.toString(i));
    		Map<ArrayList<Integer>, String[]> updatesToSend = new HashMap<ArrayList<Integer>, String[]>();
	    	replicas.put(stub, updatesToSend);
    	} catch (Exception e) {
    		System.out.println("Replica not found" + e.toString());
    	}
    }



	///
	///	Query and Update Functions
	///
	public Map<ArrayList<Integer>, String> update(ArrayList<Integer> ts, String[] args, boolean client) {
		Map<ArrayList<Integer>, String> resp = new HashMap<ArrayList<Integer>, String>();
		ArrayList<Integer> timestampClone = new ArrayList<Integer>(ts);
		boolean uptodate = false;

		// Check if the replica is as up-to-date as the frontend, and remain in the while loop whilst it is not
		while (!uptodate) {
			uptodate = true;
			for (int i = 0; i < timestamp.size(); i++) {
				if (ts.get(i) > timestamp.get(i)) {
					uptodate = false;
				}
			}
			try {
				TimeUnit.MILLISECONDS.sleep(500);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}		

		try {
			// Apply update to the backend: if successful, update() will return true. Otherwise, it will return false
			if (!db.update(args[2], args[0], args[1])) {
				// If false, check what raised the error, and return a suitable response message
				try {
					double rating = Double.parseDouble(args[1]);
					if (rating < 0 || rating > 5) {
						resp.put(timestamp, "Your submission was unsuccessful. Please enter a rating from 0-5.");
						return resp;
					}
				} catch (Exception e) {
					resp.put(timestamp, "Your submission was unsuccessful. Please ensure you've given a numerical rating.");
					return resp;
				}
			}

			// Check if the update request came from a client or a replica, and update the timestamp and logs accordingly
			if (client) {
				timestamp.set(replicaNo, timestamp.get(replicaNo)+1);
				timestampClone = new ArrayList<Integer>(timestamp);
			}
			logs.put(timestampClone, args);

			// Queue the new update to the lists of gossip for all replicas
			for (ReplicaInterface r : replicas.keySet()) {
				replicas.get(r).put(timestampClone, args);
			}

			resp.put(timestamp, "Your rating has been submitted! Thanks!");
			return resp;
		} catch (Exception e) {
			resp.put(timestamp, "Your submission was unsuccessful. Please check that your movieId is correct.");
			return resp;
		}
	}

	public Map<ArrayList<Integer>, String> query(ArrayList<Integer> ts, String[] args) {
		Map<ArrayList<Integer>, String> resp = new HashMap<ArrayList<Integer>, String>();
		boolean uptodate = false;

		// Check if the replica is as up-to-date as the frontend, and remain in the while loop whilst it is not
		while (!uptodate) {
			uptodate = true;
			for (int i = 0; i < timestamp.size(); i++) {
				if (ts.get(i) > timestamp.get(i)) {
					uptodate = false;
				}
			}
			try {
				TimeUnit.MILLISECONDS.sleep(500);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// Check type of search request, and handle accordingly
		if(args[0].equals("title")) {
			resp.put(timestamp, db.searchMovie(args[1]));
		} else if (args[0].equals("id")) {
			if (db.getTitle(args[1]) != null) {
				resp.put(timestamp, db.getTitle(args[1]) + ", Previous Rating: " + db.getUserRating(args[2], args[1]));
			} else {
				resp.put(timestamp, null);
			}
		}
		return resp;
	}



	//
	// Gossip Functions
	//
	public static void gossip() {
		// Do not gossip if the replica is "offline"
		if (status.equals("offline")) {
			return;
		}

		ReplicaInterface temp = null;
		try {
			// Randomly select a replica from the arraylist of replicas
			int index = new Random().nextInt(replicas.keySet().size());
			int i = 0;
			for (ReplicaInterface r : replicas.keySet()) {
				if (i == index) {
					temp = r;
					String stubStatus = r.status();
					if(replicas.get(r).size() > 0) {
						// Check if the recipient is ready, and gossip if so
						if (stubStatus.equals("ready")) {
							r.makeGossip(replicas.get(r), replicaNo);
							replicas.get(r).clear();
						} else {
							if (gossipFeed) {
								System.out.println("Recipient is " + stubStatus + ".");
							}
						} 
					}			
				} else {
					i++;
				}
			}
			
		} catch (Exception e) {
			if (gossipFeed && temp != null && replicas.get(temp).size() > 0) {
				System.err.println("Recipient is offline.");
			}

			// Check if the server is actually down, or if the stub was just out of date. If the stub was out of date, replace/update it
			try {
	    		Registry registry = LocateRegistry.getRegistry("127.0.0.1", port);
		    	ReplicaInterface stub = (ReplicaInterface) registry.lookup(replicaRegistries.get(temp));
		    	replicas.put(stub, replicas.remove(temp));
		    	replicaRegistries.put(stub, replicaRegistries.remove(temp));
    		} catch (Exception er) {
    			System.err.println("Recipient server not bound.");
    		}
		}
	}

	public void makeGossip(Map<ArrayList<Integer>, String[]> newUpdates, int num) {
		// Keep count of the number of new updates
		int count = 0;
		// Iterate through all the received updates
		for (ArrayList<Integer> k : newUpdates.keySet()) {
			for (int i = 0; i < k.size(); i++) {
				if (timestamp.get(i) < k.get(i)) {
					timestamp.set(i, k.get(i));
				}
			}

			// Check if the update has been previously applied
			if (!logs.keySet().contains(k)) {
				count += 1;
				update(k, newUpdates.get(k), false);
			}
		}
		if (gossipFeed) {
			System.out.println("Gossip received from Replica" + Integer.toString(num) + ", " + Integer.toString(count) + " new updates of " + Integer.toString(newUpdates.size()) + ", " + getTimestamp());
		}
	}



	//
	// Miscalaneous Functions
	//
	public static void replicate() {
		int i = 0;
		boolean replicated = false;
		while (i < replicas.size() && !replicated) {
			try {
				// Connect to a random replica and get their logs
				ReplicaInterface stub = randomStub();
				logs = stub.getLogs();
				// Pass all the entries of the log through to the database, bringing it up to date with the randomly selected server
				for (ArrayList<Integer> k : logs.keySet()) {
					// Set the timestamp values to match the highest found in the logs
					for (int j = 0; j < k.size(); j++) {
						if (timestamp.get(j) < k.get(j)) {
							timestamp.set(j, k.get(j));
						}
					}
					db.update(logs.get(k)[2], logs.get(k)[0], logs.get(k)[1]);
				}
				replicated = true;
			} catch (Exception e) {
				i++;
			}
		}
		// Make the server available again to receive new requests/gossip
		status = "ready";
	}

	public Map<ArrayList<Integer>, String[]> getLogs() {
		return logs;
	}

	public static String getTimestamp() {
		String tsString = "";
		for (int i = 0; i < timestamp.size(); i++) {
			tsString += Integer.toString(timestamp.get(i)) + ",";
		}
		return tsString.substring(0, tsString.length()-1);
	}

	public String status() {
		return status;
	}

	public static ReplicaInterface randomStub() {
		int index = new Random().nextInt(replicas.keySet().size());
		int i = 0;
		for (ReplicaInterface r : replicas.keySet()) {
			if (i == index) {
				return r;
			} else {
				i++;
			}
		}
		return null;
	}
}
