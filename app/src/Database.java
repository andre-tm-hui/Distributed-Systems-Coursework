package movie;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Database {
	// Users stored in a Map, with the key being their user IDs, and the values being an arraylist of movieID/rating pairs
	private Map<String, ArrayList<ArrayList<String>>> users = new HashMap<String, ArrayList<ArrayList<String>>>();
	// Movie Data stored in a Map, with the key being the movie ID, and the String[] containing the title, rating, and number of ratings
	private Map<String, String[]> movieData = new HashMap<String, String[]>();
	// Currently updating movies have their IDs placed in busy
	private ArrayList<String> busy = new ArrayList<String>();

	public Database() {
		try {
			System.out.print("Initializing Database...  ");

			// Parse movies.csv
			String[] args = new String[1];
			String[] movie = new String[1];
			FileReader file = new FileReader("./movies.csv");
			BufferedReader r = new BufferedReader(file);
			String s = r.readLine();
			s = r.readLine();
			while(s != null) {
				args = s.split(",");
				movie = new String[4];
				movie[0] = args[1];
				movie[1] = "0";
				movie[2] = "0";
				// Some movie titles have commas in them, so this accounts for them
				for (int i = 2; i < args.length-1; i++) {
					movie[0] = movie[0] + "," + args[i];
				}
				movieData.put(args[0], movie);
				s = r.readLine();
			}

			// Parse ratings.csv
			file = new FileReader("./ratings.csv");
			r = new BufferedReader(file);
			s = r.readLine();
			s = r.readLine();
			while(s != null) {
				args = s.split(",");
				if (!users.keySet().contains(args[0])) {
					users.put(args[0], new ArrayList<ArrayList<String>>());
				}

				movie = movieData.get(args[1]);
				movie[1] = Double.toString(((Double.parseDouble(movie[1]) * Integer.parseInt(movie[2])) + Double.parseDouble(args[2]))/(Integer.parseInt(movie[2]) + 1));
				movie[2] = Integer.toString(Integer.parseInt(movie[2]) + 1);
				s = r.readLine();
			}
			System.out.println("Loaded");
			
		} catch (Exception e) {
			System.err.println(e.toString());
		}
	}

	public String searchMovie(String title) {
		String output = "Results for \"" + title + "\":\r\n";
		output += "    Movie ID | Movie Title | Rating\r\n";
		boolean empty = true;
		// Check for matches in all titles of all movies
		for (String k : movieData.keySet()) {
			if (movieData.get(k)[0].toLowerCase().contains(title.toLowerCase())) {
				output += "    " + k + " | " + movieData.get(k)[0] + " | " +  movieData.get(k)[1] + "\r\n";
				empty = false;
			}
		}
		if (empty) {
			output = "No results found for \"" + title + "\"";
		}
		return output;
	}

	public String getTitle(String movieID) {
		try {
			return movieData.get(movieID)[0];
		} catch (Exception e) {
			// If the movie ID does not exist, return null
			return null;
		}
		
	}

	public String getRating(String movieID) {
		return movieData.get(movieID)[1];
	}

	public String getUserRating(String userID, String movieID) {
		try {
			for (ArrayList<String> ratings : users.get(userID)) {
				if (ratings.get(0).equals(movieID)) {
					return ratings.get(1);
				}
			}
			// If the user has never submitted a previous rating for this movie
			return null;
		} catch (Exception e) {
			// If the user has never submitted a rating before
			return null;
		}
	}

	public boolean update(String userID, String movieID, String rating) {
		while (busy.contains(movieID)) {
			// Do nothing whilst the movie to be updated is still in use
		}

		// Check the rating for validity
		try {
			double r = Double.parseDouble(rating);
			if (r > 5 || r < 0) {
				return false;
			}
		} catch (Exception e) {
			return false;
		}

		// Mark the movie as being used
		busy.add(movieID);

		// Check if the user has previously rated a movie before
		if (!users.keySet().contains(userID)) {
			// If not, add them to the map of users
			users.put(userID, new ArrayList<ArrayList<String>>());
		}

		boolean contains = false;
		// Check if the user has rated this movie before
		for (ArrayList<String> ratings : users.get(userID)) {
			if (ratings.get(0).equals(movieID)) {
				contains = true;
				String[] movie = movieData.get(movieID);
				movie[1] = Double.toString(((Double.parseDouble(movie[1]) * Integer.parseInt(movie[2])) + Double.parseDouble(rating) - Double.parseDouble(ratings.get(1)))/(Integer.parseInt(movie[2])));
				ratings.set(1, rating);
			}
		}
		// If not, add this movie to the user, and create a new rating.
		if (!contains) {
			ArrayList<String> data = new ArrayList<String>();
			data.add(movieID);
			data.add(rating);
			users.get(userID).add(data);

			String[] movie = movieData.get(movieID);
			movie[1] = Double.toString(((Double.parseDouble(movie[1]) * Integer.parseInt(movie[2])) + Double.parseDouble(rating))/(Integer.parseInt(movie[2]) + 1));
			movie[2] = Integer.toString(Integer.parseInt(movie[2]) + 1);
		}

		// Mark the movie as not in use
		busy.remove(movieID);

		return true;
	}
}