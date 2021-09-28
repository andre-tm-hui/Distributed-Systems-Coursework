package movie;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.ArrayList;

public interface ReplicaInterface extends Remote {
	Map<ArrayList<Integer>, String> query(ArrayList<Integer> ts, String[] args) throws RemoteException;
	Map<ArrayList<Integer>, String> update(ArrayList<Integer> ts, String[] args, boolean client) throws RemoteException;
	Map<ArrayList<Integer>, String[]> getLogs() throws RemoteException;
	String status() throws RemoteException;
	void makeGossip(Map<ArrayList<Integer>, String[]> newUpdates, int num) throws RemoteException;
	void addReplica(int i) throws RemoteException;
}
