package movie;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface FrontEndInterface extends Remote {
	String request(String[] args) throws RemoteException;
}
