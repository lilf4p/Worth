import java.util.ArrayList;
import java.util.List;

public class Progetto {

    /*OVERVIEW : un progetto e' costituito da una serie di card divise in 4 liste,
     sono ammessi solo alcuni spostamenti tra liste*/

    private final String nome;
    private final ArrayList<Card> todo;
    private final ArrayList<Card> inprogress;
    private final ArrayList<Card> toberevised;
    private final ArrayList<Card> done;
    private final ArrayList<String> listaMembri; //utenti che possono interagire col progetto

    public Progetto (String nome) { //crea un nuovo progetto
        this.nome = nome;
        todo = new ArrayList<>();
        inprogress = new ArrayList<>();
        toberevised = new ArrayList<>();
        done = new ArrayList<>();
        listaMembri = new ArrayList<>();
    }

    public String getNome() {
        return nome;
    }

    public void addMember (String nickUtente) throws MemberAlreadyExistException {
        if (listaMembri.contains(nickUtente)) throw new MemberAlreadyExistException();
        listaMembri.add(nickUtente);
    }

    public ArrayList<String> getListMembers () {
        return listaMembri;
    }

    public void addCard (String cardName, String descrizione) throws CardAlreadyExistException {
        try {
            getCard(cardName);
        } catch (CardNotFoundException e) {
            todo.add(new Card(cardName,descrizione));
            return;
        }
        throw new CardAlreadyExistException();
    }

    public ArrayList<Card> getListCards () {
        ArrayList<Card> allCards = new ArrayList<>();
        allCards.addAll(todo);
        allCards.addAll(inprogress);
        allCards.addAll(toberevised);
        allCards.addAll(done);
        return allCards;
    }

    //restituisce la card cardName se appartiene ad una delle liste del progetto, lancia eccezione altrimenti
    public Card getCard (String cardName) throws CardNotFoundException {
        int index = contains(cardName,todo);
        if (index>-1) return todo.get(index);
        index = contains(cardName,inprogress);
        if (index>-1) return inprogress.get(index);
        index = contains(cardName, toberevised);
        if (index>-1) return toberevised.get(index);
        index = contains(cardName,done);
        if (index>-1) return done.get(index);
        throw new CardNotFoundException();
    }

    public ArrayList<String> getCardHistory (String cardName) throws CardNotFoundException {
        return getCard(cardName).getHistory();
    }

    public void moveCard (String cardName, String listaPartenza, String listaDestinazione) throws CardNotFoundException, WrongStartListException, ListNotFoundException, IllegalMoveException {
       Card card = getCard(cardName);//se la card non esiste nel progetto viene lanciata eccezione
        ArrayList<Card> startList = getListCard(listaPartenza);//se le liste non sono tra le 4 predefinite lancio eccezione
        ArrayList<Card> destList = getListCard(listaDestinazione);
       if (!(startList.contains(card))) throw new WrongStartListException();//card non si trova nella lista di partenza
       /*controlla se spostamento possibile :
       * -tod0 --> inprogress
       * -inprogress --> toberevised
       * -toberevised --> done
       * -inprogress --> done
       * -toberevised --> inprogress */
        if ( (listaPartenza.equals("todo") && listaDestinazione.equals("inprogress")) || (listaPartenza.equals("inprogress") && (listaDestinazione.equals("toberevised") || listaDestinazione.equals("done")))
                || (listaPartenza.equals("toberevised") && (listaDestinazione.equals("done") || listaDestinazione.equals("inprogress"))) ) {
            startList.remove(card);
            destList.add(card);
            card.addToHistory("Card " + cardName + " spostata dalla lista " + listaPartenza + " alla lista " + listaDestinazione);//aggiunge l'evento alla storia della card
        }else throw new IllegalMoveException();
    }

    private int contains (String cardName, List<Card> list) {
        for (int i=0;i<list.size();i++) {
           if (list.get(i).getNome().equals(cardName)) return i;
        }
        return -1;
    }

    private ArrayList<Card> getListCard (String listName) throws ListNotFoundException {
        if ("todo".equals(listName)) {
            return todo;
        } else if ("inprogress".equals(listName)) {
            return inprogress;
        } else if ("toberevised".equals(listName)) {
            return toberevised;
        } else if ("done".equals(listName)) {
            return done;
        } else {
            throw new ListNotFoundException();
        }
    }

}
