
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.io.*;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteServer;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server extends RemoteServer implements ServerInt {

    final static int DEFAULT_PORT_SERVER = 2000;
    final static int DEFAULT_PORT_RMI = 1950;
    final static int MAX_SEG_SIZE = 512;
    //HashMap <username, hash> -> contiene le info per il login
    private ConcurrentHashMap<String,String> listUser;
    // contiene tutti i progetti creati
    private ArrayList<Progetto> listProgetti;
    // TODO: 23/01/21 RENDERE PERSISTENTE I PROGETTI SU DISCO
    //coppie socket add client - username --> con quale username il client si e' loggato
    private HashMap<String,String> userOnline;

    public Server () throws RemoteException {
        //AVVIO IL SERVER -> RIPRISTINO LO STATO -> PERSISTENZA
        listUser = new ConcurrentHashMap<>();
        System.out.println("listUser inizializzata");
        if (listUser==null) System.out.println("listUser e' null");
        //setto file di login --> se non esiste lo creo, se esiste ripristino il contenuto
        File loginFile = new File ("Login.json");
        if (loginFile.length()>0) {
            try {
                Gson gson = new Gson();
                Type type = new TypeToken<ConcurrentHashMap<String,String>>(){}.getType();
                Reader reader = Files.newBufferedReader(loginFile.toPath());
                listUser = gson.fromJson(reader,type);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else{
            try {
                loginFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        listProgetti = new ArrayList<>();
        // TODO: 23/01/21 caricare i progetti dal disco
        userOnline = new HashMap<>();
    }

    //metodo rmi x registrazione
    public String register(String username, String password) throws RemoteException {
        try {
            //HASH PASSWORD CON BCRYPT
            String hash = BCrypt.hashpw(password,BCrypt.gensalt());
            System.out.println("Password inviata per register : "+password);
            System.out.println("Username inviato per register : "+username);
            //System.out.println(password.length());
            if (listUser==null) System.out.println("LIST USER E' NULL");
            if (listUser.putIfAbsent(username, hash) != null) return "Errore : username gia' utilizzato";
            //RENDO PERMANENTE SU FILE
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Writer writer = new FileWriter("Login.json");
            gson.toJson(listUser, writer);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "ok";
    }

    public static void main(String[] args) {

        System.out.println("Server starting ... listening on port "+DEFAULT_PORT_SERVER);

        ServerSocketChannel serverSocketChannel;
        Selector selector;
        Server server;
        try {
            //CREO RMI
            server = new Server();
            ServerInt stub = (ServerInt) UnicastRemoteObject.exportObject(server,0);
            LocateRegistry.createRegistry(DEFAULT_PORT_RMI);
            Registry r = LocateRegistry.getRegistry(DEFAULT_PORT_RMI);
            r.rebind("WORTH-SERVER",stub);
            //setto socket e apro il selector
            serverSocketChannel = ServerSocketChannel.open();
            ServerSocket serverSocket = serverSocketChannel.socket();
            serverSocket.bind(new InetSocketAddress(DEFAULT_PORT_SERVER));
            serverSocketChannel.configureBlocking(false);
            selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        while (true) {
            try {
                selector.select();
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
            Set <SelectionKey> readyKeys = selector.selectedKeys();
            Iterator <SelectionKey> iterator = readyKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                //rimuove la chiave dal selected set , ma non dal registered set
                try {
                    if (key.isAcceptable()) {
                        //creo connessione tcp con il client
                        SocketChannel client = serverSocketChannel.accept();
                        System.out.println("Connection successfully accepted from "+client);
                        client.configureBlocking(false);
                        SelectionKey keyClient = client.register(selector,SelectionKey.OP_READ);
                        String s = "Benvenuto in Worth sviluppato da Leonardo Stoppani";
                        byte [] bytes = s.getBytes();
                        ByteBuffer output1 = ByteBuffer.allocate(bytes.length);
                        output1.put(bytes);
                        output1.flip();
                        client.write(output1);
                        System.out.println("SEND : Welcome msg\n");
                        //System.out.println(client.getRemoteAddress().toString());
                    }else if (key.isReadable()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        ByteBuffer input = ByteBuffer.allocate(MAX_SEG_SIZE);
                        if (client.read(input)==-1) { //leggo dal channel tutta la stringa
                            System.out.println("Closing connection with client...");
                            key.cancel();
                            key.channel().close();
                            continue;
                        }
                        input.flip();//preparo buffer alla lettura
                        String si = new String(input.array(), StandardCharsets.UTF_8);
                        System.out.println("FROM CLIENT : "+si);
                        //System.out.println(si.length());
                        // TODO: 21/01/21 parsing dei comandi + implementazione comandi
                        StringTokenizer tokenizer = new StringTokenizer(si);
                        String cmd = tokenizer.nextToken().trim();
                        System.out.println("CMD : "+cmd);
                        //PARAMETRI CORRETTI PERCHE' GIA' CONTROLLATI DAL CLIENT
                        ByteBuffer response = ByteBuffer.allocate(MAX_SEG_SIZE);
                        switch (cmd) {
                            case "login" : {
                                String username = tokenizer.nextToken();
                                String passw = tokenizer.nextToken().trim();
                                System.out.println("Password inviata per login :"+passw);
                                System.out.println(passw.length());
                                //if (username==null) System.out.println("USERNAME E' NULL");
                                ConcurrentHashMap<String,String> listUser = server.listUser;
                                if (listUser == null) System.out.println("listUser e' null");
                                String hash = listUser.get(username);  //BUG IMMENSO NULLPOINTER
                                if (hash == null) {//utente non registrato
                                    String s = "Errore : utente " + username + " non esiste";
                                    response.put(s.getBytes());
                                } else { //utente registrato, controllo passw
                                    if (BCrypt.checkpw(passw, hash)) {
                                        if (server.userOnline.putIfAbsent(client.getRemoteAddress().toString(),username) == null) {
                                            response.put((username + " logged in").getBytes());
                                            System.out.println("PASSW OK");
                                        }else {
                                            response.put("Errore : c'e' un utente gia' collegato, deve essere prima scollegato".getBytes());
                                            System.out.println("ALTRO UTENTE GIA' LOGGATO");
                                        }
                                    } else {
                                        response.put(("Errore : password errata").getBytes());
                                        System.out.println("PASSW ERRATA");
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "logout" : {
                                String clientAddress = client.getRemoteAddress().toString();
                                String usernameLoggato = server.userOnline.get(clientAddress);
                                if (server.userOnline.remove(clientAddress)==null) response.put("Errore : utente non loggato".getBytes());
                                else response.put((usernameLoggato+" scollegato").getBytes());
                                key.attach(response);
                                break;
                            }
                            case "listProjects" : {
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                if (username == null) {
                                    response.put("Errore : utente non loggato".getBytes());
                                }else {
                                    ArrayList<Progetto> listProgetti = server.listProgetti;
                                    String sProgetti = "";
                                    for (Progetto p : listProgetti) {
                                        if (p.getListMembers().contains(username)) {
                                            sProgetti = sProgetti+p.getNome();
                                            sProgetti = sProgetti+" ";
                                        }
                                    }
                                    if (sProgetti.length()==0) response.put("Non fai parte di alcun progetto".getBytes());
                                    else response.put(sProgetti.getBytes());
                                }
                                key.attach(response);
                                break;
                            }
                            case "createProject" : {
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                String nameProject = tokenizer.nextToken().trim();
                                if (username==null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    if (server.contains(nameProject)) response.put("Errore : nome del progetto non disponibile".getBytes());
                                    else {
                                        Progetto p = new Progetto(nameProject);
                                        try {
                                            p.addMember(username);
                                            server.listProgetti.add(p);
                                            response.put("Progetto creato con successo".getBytes());
                                        } catch (MemberAlreadyExistException e) {
                                            System.out.println("Utente e' gia' membro del progetto");
                                        }
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "addMember" : {
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                String projectName = tokenizer.nextToken();
                                String userToAdd = tokenizer.nextToken().trim();
                                if (username==null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    if (!server.listUser.containsKey(userToAdd))
                                        response.put(("Errore : utente " + userToAdd + " non registrato").getBytes());
                                    else {
                                        Progetto p = server.getProgetto(projectName);
                                        if (p == null) response.put("Errore : il progetto non esiste".getBytes());
                                        else if (!p.getListMembers().contains(username)) response.put("Errore : non sei un membro del progetto".getBytes());
                                        else {
                                            try {
                                                p.addMember(userToAdd);
                                                response.put(("Utente "+userToAdd+" aggiunto al progetto con successo").getBytes());
                                            } catch (MemberAlreadyExistException e) {
                                                response.put(("Utente "+userToAdd+" e' gia' un membro del progetto").getBytes());
                                            }
                                        }
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "showMembers" : {
                                String projectName = tokenizer.nextToken().trim();
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                if (username==null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    Progetto p = server.getProgetto(projectName);
                                    if (p==null) response.put("Errore : il progetto non esiste".getBytes());
                                    else if (!p.getListMembers().contains(username)) response.put("Errore : non sei un membro del progetto".getBytes());
                                    else {
                                        ArrayList<String> listMembers = p.getListMembers();
                                        String sList = "";
                                        for (String s : listMembers) {
                                            sList = sList+s;
                                            sList = sList+" ";
                                        }
                                        response.put(sList.getBytes());
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "showCards" : {
                                String projectName = tokenizer.nextToken().trim();
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                if (username==null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    Progetto p = server.getProgetto(projectName);
                                    if (p==null) response.put("Errore : il progetto non esiste".getBytes());
                                    else if (!p.getListMembers().contains(username)) response.put("Errore : non sei un membro del progetto".getBytes());
                                    else {
                                        ArrayList<Card> listCards = p.getListCards();
                                        String cList = "";
                                        for (Card c : listCards) {
                                            cList = cList+c.getNome();
                                            cList = cList+" ";
                                        }
                                        response.put(cList.getBytes());
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "showCard" : {
                                String projectName = tokenizer.nextToken();
                                String cardName = tokenizer.nextToken().trim();
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                if (username==null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    Progetto p = server.getProgetto(projectName);
                                    if (p==null) response.put("Errore : il progetto non esiste".getBytes());
                                    else if (!p.getListMembers().contains(username)) response.put("Errore : non sei un membro del progetto".getBytes());
                                    else {
                                        try {
                                            Card card = p.getCard(cardName);
                                            response.put(("card: nome="+card.getNome()+", descrizione="+card.getDescrizione()+", stato="+card.getStato()).getBytes());
                                        } catch (CardNotFoundException e) {
                                            response.put(("Errore : la card "+cardName+" non esiste nel progetto "+projectName).getBytes());
                                        }
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "addCard" : {
                                String projectName = tokenizer.nextToken();
                                String cardName = tokenizer.nextToken();
                                String descrizione = "";
                                while (tokenizer.hasMoreTokens()) {
                                    descrizione = descrizione + tokenizer.nextToken();
                                }
                                String descrizioneComp = descrizione.trim();
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                if (username==null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    Progetto p = server.getProgetto(projectName);
                                    if (p==null) response.put("Errore : il progetto non esiste".getBytes());
                                    else if (!p.getListMembers().contains(username)) response.put("Errore : non sei un membro del progetto".getBytes());
                                    else {
                                        try {
                                            p.addCard(cardName,descrizioneComp);
                                            response.put(("Card "+cardName+" aggiunta al progetto "+projectName+" con successo").getBytes());
                                        } catch (CardAlreadyExistException e) {
                                            response.put(("Card "+cardName+" esiste gia' nel progetto "+projectName).getBytes());
                                        }
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "moveCard" : {
                                String projectName = tokenizer.nextToken();
                                String cardName = tokenizer.nextToken();
                                String listStart = tokenizer.nextToken();
                                String listDest = tokenizer.nextToken().trim();
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                if (username==null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    Progetto p = server.getProgetto(projectName);
                                    if (p==null) response.put("Errore : il progetto non esiste".getBytes());
                                    else if (!p.getListMembers().contains(username)) response.put("Errore : non sei un membro del progetto".getBytes());
                                    else {
                                        try {
                                            p.moveCard(cardName, listStart, listDest);
                                            response.put("ok".getBytes());
                                        } catch (CardNotFoundException e) {
                                            response.put(("Errore : Card "+cardName+" non esiste nel progetto "+projectName).getBytes());
                                        } catch (WrongStartListException e) {
                                            response.put(("Errore : Card "+cardName+" non fa parte della lista "+listStart).getBytes());
                                        } catch (ListNotFoundException e) {
                                            response.put("Errore : liste non valide, le liste consentite sono todo,inprogress,toberevised,done".getBytes());
                                        } catch (IllegalMoveException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "getCardHistory" : {
                                String projectName = tokenizer.nextToken();
                                String cardName = tokenizer.nextToken();
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                if (username==null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    Progetto p = server.getProgetto(projectName);
                                    if (p==null) response.put("Errore : il progetto non esiste".getBytes());
                                    else if (!p.getListMembers().contains(username)) response.put("Errore : non sei un membro del progetto".getBytes());
                                    else {
                                        try {
                                            ArrayList<String> cardHistory = p.getCardHistory(cardName);
                                            String sList = "";
                                            for (String s : cardHistory) {
                                                sList = sList + s;
                                                sList = sList + "\n";
                                            }
                                            response.put(sList.getBytes());
                                        } catch (CardNotFoundException e) {
                                            response.put(("Errore : Card "+cardName+" non esiste nel progetto "+projectName).getBytes());
                                        }
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            default:
                                response.put("Comando non riconosciuto dal server".getBytes());
                                key.attach(response);
                        }
                        key.interestOps(SelectionKey.OP_WRITE);
                    }else if (key.isWritable()) {
                        //mando il risultato del comando al client
                        SocketChannel client = (SocketChannel) key.channel();
                        ByteBuffer output2 = ByteBuffer.allocate(MAX_SEG_SIZE);
                        output2 = (ByteBuffer) key.attachment();
                        if (output2 == null) System.out.println("Attachment vuoto --> response vuoto --> manca una risposta");
                        output2.flip();
                        //System.out.println(new String(output2.array(),StandardCharsets.UTF_8));
                        client.write(output2);
                        System.out.println("SEND : Response");
                        key.interestOps(SelectionKey.OP_READ);
                    }
                } catch (IOException e) {
                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                    e.printStackTrace();
                }
            }
        }
    }
    //METODO DI SUPPORTO A UNIVOCITA' NOME PROGETTO - TRUE se esiste gia' un progetto di nome nameProject, FALSE altrimenti
    private boolean contains (String nameProject) {
        for (Progetto p : listProgetti) {
            if(p.getNome().equals(nameProject)) return true;
        }
        return false;
    }

    private Progetto getProgetto (String nameProject) {
        for (Progetto p : listProgetti) {
            if (p.getNome().equals(nameProject)) return p;
        }
        return null;
    }

}
