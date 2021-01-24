import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerInt extends Remote {
    //registrazione utente a Worth
    String register (String nickUtente, String password) throws RemoteException;
    //registrazione callback
    public void registerForCallback (ClientInt clientInterface) throws RemoteException;
    //cancella registrazione callback
    public void unregisterForCallback (ClientInt clientInterface) throws RemoteException;
}
