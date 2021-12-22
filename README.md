# Distributed Systems Coursework
In this coursework, a virtual distributed system is made, where users can access, update and query a database, stored redundantly on multiple different servers/locations. These different servers then keep each other updated periodically.

It is recommended that Windows is used for this program.

Usage Instructions:
- Have java in your PATH
- Extract the app folder
- Make sure "movies.csv" and "ratings.csv" are in the app/src folder with the java files (6 .JAVA files in total)

For Windows:
- Run "runServers.bat" in the app folder
- When the FrontEnd server is finished binding (console will indicate), you may run "Client.bat"

For UNIX Systems:
- Navigate to the app/src folder in a console
- Run "javac -d ./ Client.java FrontEnd.java FrontEndInterface.java Replica.java ReplicaInterface.java Database.java"
- In separate terminal/console windows, run in order:
	1. "rmiregistry 37001" (can be in the background)
	2. "java -Djava.security.policy=server.policy movie.Replica *n* *interval(ms)*" (run this n times)
	3. "java -Djava.security.policy=server.policy movie.FrontEnd *n*"
	4. "java -Djava.security.policy=client.policy movie.Client"

In the case a replica is shut down completely:
- Run "Replica.bat"/"java -Djava.security.policy=server.policy movie.Replica *n* *interval(ms)*" once for every server 
  that shut down
- Enter "replicate" once the server is loaded

In the case the frontend is shut down completely:
- Run "FrontEnd.bat"/"FrontEnd.sh"

To change the number of replicas and/or the gossip interval:
- Edit "runFrontEnd.bat", "runReplica.bat" such that it looks like:
		java -Djava.security.policy=server.policy movie.Replica *Number of replicas* *interval(ms)*
		java -Djava.security.policy=server.policy movie.FrontEnd *Number of replicas*
  respectively.
- Edit "runServers.bat" such that there are the same number of "START runReplica.bat"s as there are number of replicas

Usage Instructions of the Client/Replicas are given in the programs.




Features include:
- Expandable - Specify any number of Replicas in the system greater than 3 (default is 4)
- Search - Users can search for movie titles and receive data associated with matching titles
- Rating - Users can rate movies by movie ID
- Update Ratings - Users can change ratings they've made previously
- Toggle Replica Status - Replicas can have their states changed on command:
				- Ready: Allow all incoming requests and continue gossiping
				- Overloaded: Disallow incoming requests, continue gossiping
				- Offline: Disallow incoming requests and stop gossiping
- Automatic Server Selection - The frontend randomly selects a replica to connect to, and notifies the client if servers
			       are unavailable
- Toggle Feed - You can choose whether or not to view a live feed of gossip on the Replicas
- Restore Replicas - If a server completely fails, another server can take its place on the vacant registry and
	 	     replicate another server to ensure it is up to date
- Gossiping - Servers randomly select another server to gossip to, at a variable interval, to ensure that all
	      servers remain up to date with each other. The interval can be set to any number of milliseconds, and will
	      default to 1000ms if no interval was specified/the input is not a number.
