

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

// --- Model classes ---
class Flashcard implements Serializable {
    private static final long serialVersionUID = 1L;
    private String question;
    private String answerRaw; // comma-separated

    // SM-2 fields
    private double easeFactor = 2.5;
    private int repetition = 0;
    private int interval = 0; // days
    private LocalDate nextReview = LocalDate.now();
    private LocalDate lastReview = null;

    public Flashcard(String q, String a) {
        this.question = q;
        this.answerRaw = a;
    }

    public String getQuestion() { return question; }
    public String getAnswer() { return answerRaw; }

    private String normalize(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase(Locale.ROOT);
        t = t.replaceAll("[^\p{IsAlphabetic}\p{IsDigit}\s'\-]", "");
        t = t.replaceAll("\s+", " ");
        return t;
    }

    public boolean checkAnswer(String userInput) {
        if (userInput == null) return false;
        String norm = normalize(userInput);
        if (norm.isEmpty()) return false;
        String[] parts = answerRaw.split("\s*,\s*");
        for (String p : parts) {
            if (normalize(p).equals(norm)) return true;
        }
        return false;
    }

    public LocalDate getNextReview() { return nextReview; }
    public void setNextReview(LocalDate d) { nextReview = d; }
    public String schedulingInfo() {
        return String.format("Next=%s | EF=%.2f | rep=%d | int=%dd",
                nextReview, easeFactor, repetition, interval);
    }

    public void updateSM2(int quality) {
        lastReview = LocalDate.now();
        quality = Math.max(0, Math.min(5, quality));
        if (quality >= 3) {
            if (repetition == 0) interval = 1;
            else if (repetition == 1) interval = 6;
            else interval = (int)Math.round(interval * easeFactor);
            repetition++;
        } else {
            repetition = 0;
            interval = 1;
        }
        double ef = easeFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
        if (ef < 1.3) ef = 1.3;
        easeFactor = ef;
        nextReview = LocalDate.now().plusDays(interval);
    }
}

class Deck implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private List<Flashcard> cards = new ArrayList<>();

    public Deck(String name) { this.name = name; }
    public String getName() { return name; }
    public void setName(String n) { name = n; }
    public List<Flashcard> getCards() { return cards; }
    public void addCard(Flashcard c) { cards.add(c); }
    public void removeCard(int i) { if (i>=0 && i<cards.size()) cards.remove(i); }
}

// --- Persistence helper ---
class Storage {
    private static final String FILE = "decks_sm2_gui.ser";

    public static void save(List<Deck> decks) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE))) {
            oos.writeObject(decks);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Deck> load() throws IOException, ClassNotFoundException {
        File f = new File(FILE);
        if (!f.exists()) return new ArrayList<>();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            return (List<Deck>) ois.readObject();
        }
    }
}

// --- Swing GUI ---
public class FlashcardAppSwing extends JFrame {
    private DefaultListModel<Deck> deckListModel = new DefaultListModel<>();
    private JList<Deck> deckJList = new JList<>(deckListModel);
    private DefaultTableModel cardTableModel = new DefaultTableModel(new Object[]{"#","Question","Answer","Scheduling"},0) {
        public boolean isCellEditable(int r,int c){return false;}
    };
    private JTable cardTable = new JTable(cardTableModel);

    private List<Deck> decks = new ArrayList<>();

    public FlashcardAppSwing() {
        setTitle("Flashcard App — SM-2 (Swing)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900,600);
        setLocationRelativeTo(null);
        initUI();
        loadData();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(8,8));
        root.setBorder(new EmptyBorder(8,8,8,8));

        // Left: Decks
        JPanel left = new JPanel(new BorderLayout(6,6));
        left.add(new JLabel("Decks"), BorderLayout.NORTH);
        deckJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deckJList.setCellRenderer(new DefaultListCellRenderer(){
            public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean isSelected,boolean cellHasFocus){
                super.getListCellRendererComponent(list,value,index,isSelected,cellHasFocus);
                if (value instanceof Deck) setText(((Deck)value).getName()+" ("+((Deck)value).getCards().size()+")");
                return this;
            }
        });
        left.add(new JScrollPane(deckJList), BorderLayout.CENTER);

        JPanel deckButtons = new JPanel(new GridLayout(0,1,4,4));
        JButton createDeckBtn = new JButton("Create Deck");
        JButton renameDeckBtn = new JButton("Rename Deck");
        JButton deleteDeckBtn = new JButton("Delete Deck");
        JButton saveBtn = new JButton("Save");
        deckButtons.add(createDeckBtn); deckButtons.add(renameDeckBtn); deckButtons.add(deleteDeckBtn); deckButtons.add(saveBtn);
        left.add(deckButtons, BorderLayout.SOUTH);

        // Right: Cards table + card actions
        JPanel right = new JPanel(new BorderLayout(6,6));
        right.add(new JLabel("Cards"), BorderLayout.NORTH);
        cardTable.setFillsViewportHeight(true);
        right.add(new JScrollPane(cardTable), BorderLayout.CENTER);

        JPanel cardButtons = new JPanel(new GridLayout(0,1,4,4));
        JButton addCardBtn = new JButton("Add Card");
        JButton editCardBtn = new JButton("Edit Card");
        JButton removeCardBtn = new JButton("Remove Card");
        JButton quizBtn = new JButton("Start Quiz (Deck)");
        JButton reviewDueBtn = new JButton("Review Due Cards");
        cardButtons.add(addCardBtn); cardButtons.add(editCardBtn); cardButtons.add(removeCardBtn); cardButtons.add(quizBtn); cardButtons.add(reviewDueBtn);
        right.add(cardButtons, BorderLayout.EAST);

        root.add(left, BorderLayout.WEST);
        root.add(right, BorderLayout.CENTER);
        add(root);

        // Layout tuning
        left.setPreferredSize(new Dimension(260, getHeight()));

        // Listeners
        deckJList.addListSelectionListener(this::onDeckSelected);
        createDeckBtn.addActionListener(e -> onCreateDeck());
        renameDeckBtn.addActionListener(e -> onRenameDeck());
        deleteDeckBtn.addActionListener(e -> onDeleteDeck());
        saveBtn.addActionListener(e -> onSave());

        addCardBtn.addActionListener(e -> onAddCard());
        removeCardBtn.addActionListener(e -> onRemoveCard());
        editCardBtn.addActionListener(e -> onEditCard());
        quizBtn.addActionListener(e -> onQuizDeck());
        reviewDueBtn.addActionListener(e -> onReviewDueCards());

        // Double click card to quiz that card
        cardTable.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e){
                if (e.getClickCount()==2) onQuizSingleCard();
            }
        });

        // Window close save
        addWindowListener(new WindowAdapter(){
            public void windowClosing(WindowEvent e){ try { Storage.save(decks); } catch (Exception ex) { /* ignore */ } }
        });
    }

    private void loadData(){
        try {
            decks = Storage.load();
            for (Deck d: decks) deckListModel.addElement(d);
            if (!decks.isEmpty()) deckJList.setSelectedIndex(0);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to load data: " + e.getMessage(), "Load error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDeckSelected(ListSelectionEvent ev){
        if (!ev.getValueIsAdjusting()) refreshCardTable();
    }

    private void refreshCardTable(){
        cardTableModel.setRowCount(0);
        Deck sel = deckJList.getSelectedValue();
        if (sel==null) return;
        List<Flashcard> cards = sel.getCards();
        for (int i=0;i<cards.size();i++){
            Flashcard c = cards.get(i);
            cardTableModel.addRow(new Object[]{i+1, c.getQuestion(), c.getAnswer(), c.schedulingInfo()});
        }
    }

    private void onCreateDeck(){
        String name = JOptionPane.showInputDialog(this, "Enter deck name:");
        if (name==null) return; name = name.trim();
        if (name.isEmpty()){ JOptionPane.showMessageDialog(this, "Name cannot be empty."); return; }
        Deck d = new Deck(name); decks.add(d); deckListModel.addElement(d); deckJList.setSelectedValue(d,true);
    }

    private void onRenameDeck(){
        Deck sel = deckJList.getSelectedValue(); if (sel==null){ JOptionPane.showMessageDialog(this,"Select a deck first."); return; }
        String nm = JOptionPane.showInputDialog(this, "Enter new name:", sel.getName()); if (nm==null) return; nm=nm.trim(); if (nm.isEmpty()){ JOptionPane.showMessageDialog(this,"Name empty."); return; }
        sel.setName(nm); deckJList.repaint();
    }

    private void onDeleteDeck(){
        Deck sel = deckJList.getSelectedValue(); if (sel==null){ JOptionPane.showMessageDialog(this,"Select a deck first."); return; }
        int ok = JOptionPane.showConfirmDialog(this, "Delete deck '"+sel.getName()+"'?","Confirm",JOptionPane.YES_NO_OPTION);
        if (ok==JOptionPane.YES_OPTION){ decks.remove(sel); deckListModel.removeElement(sel); cardTableModel.setRowCount(0); }
    }

    private void onSave(){
        try { Storage.save(decks); JOptionPane.showMessageDialog(this,"Saved."); } catch (Exception e){ JOptionPane.showMessageDialog(this,"Save failed: "+e.getMessage()); }
    }

    private void onAddCard(){
        Deck sel = deckJList.getSelectedValue(); if (sel==null){ JOptionPane.showMessageDialog(this,"Select a deck first."); return; }
        JTextField qf = new JTextField(); JTextField af = new JTextField();
        Object[] fields = {"Question:", qf, "Answer(s) (comma-separated):", af};
        int res = JOptionPane.showConfirmDialog(this, fields, "Add Flashcard", JOptionPane.OK_CANCEL_OPTION);
        if (res==JOptionPane.OK_OPTION){ String q=qf.getText().trim(); String a=af.getText().trim(); if (q.isEmpty()||a.isEmpty()){ JOptionPane.showMessageDialog(this,"Both fields required."); return;} sel.addCard(new Flashcard(q,a)); refreshCardTable(); deckJList.repaint(); }
    }

    private void onRemoveCard(){
        Deck sel = deckJList.getSelectedValue(); if (sel==null){ JOptionPane.showMessageDialog(this,"Select a deck first."); return; }
        int r = cardTable.getSelectedRow(); if (r<0){ JOptionPane.showMessageDialog(this,"Select a card row first."); return; }
        int idx = (int)cardTableModel.getValueAt(r,0)-1;
        sel.removeCard(idx); refreshCardTable(); deckJList.repaint();
    }

    private void onEditCard(){
        Deck sel = deckJList.getSelectedValue(); if (sel==null){ JOptionPane.showMessageDialog(this,"Select a deck first."); return; }
        int r = cardTable.getSelectedRow(); if (r<0){ JOptionPane.showMessageDialog(this,"Select a card row first."); return; }
        int idx = (int)cardTableModel.getValueAt(r,0)-1;
        Flashcard c = sel.getCards().get(idx);
        JTextField qf = new JTextField(c.getQuestion()); JTextField af = new JTextField(c.getAnswer());
        Object[] fields = {"Question:", qf, "Answer(s) (comma-separated):", af};
        int res = JOptionPane.showConfirmDialog(this, fields, "Edit Flashcard", JOptionPane.OK_CANCEL_OPTION);
        if (res==JOptionPane.OK_OPTION){ String q=qf.getText().trim(); String a=af.getText().trim(); if (q.isEmpty()||a.isEmpty()){ JOptionPane.showMessageDialog(this,"Both fields required."); return;} // replace
            sel.getCards().set(idx, new Flashcard(q,a)); refreshCardTable(); }
    }

    private void onQuizDeck(){
        Deck sel = deckJList.getSelectedValue(); if (sel==null){ JOptionPane.showMessageDialog(this,"Select a deck first."); return; }
        List<Flashcard> cards = new ArrayList<>(sel.getCards()); if (cards.isEmpty()){ JOptionPane.showMessageDialog(this,"Deck empty."); return; }
        Collections.shuffle(cards);
        int score=0;
        for (Flashcard c: cards){
            String ans = JOptionPane.showInputDialog(this, "Q: "+c.getQuestion());
            if (ans==null) { /* user canceled — stop quiz */ break; }
            boolean ok = c.checkAnswer(ans);
            if (ok) score++;
            String msg = ok?"Correct!":"Wrong. Ans: "+c.getAnswer();
            // quality dialog
            String qstr = JOptionPane.showInputDialog(this, msg+"
Rate quality (0-5) or leave empty to auto-rate:");
            int quality;
            if (qstr==null || qstr.trim().isEmpty()) quality = ok?5:2; else try{ quality = Integer.parseInt(qstr.trim()); } catch(Exception ex){ quality = ok?5:2; }
            c.updateSM2(quality);
        }
        JOptionPane.showMessageDialog(this, "Quiz over. Score: " + score + "/" + cards.size());
        refreshCardTable();
    }

    private void onReviewDueCards(){
        LocalDate today = LocalDate.now();
        Map<Deck, List<Flashcard>> due = new LinkedHashMap<>();
        for (Deck d: decks){
            List<Flashcard> list = new ArrayList<>();
            for (Flashcard c: d.getCards()) if (!c.getNextReview().isAfter(today)) list.add(c);
            if (!list.isEmpty()) due.put(d, list);
        }
        if (due.isEmpty()){ JOptionPane.showMessageDialog(this, "No cards due today — nice!"); return; }
        for (Map.Entry<Deck,List<Flashcard>> e: due.entrySet()){
            Deck d = e.getKey();
            for (Flashcard c: e.getValue()){
                String ans = JOptionPane.showInputDialog(this, "["+d.getName()+"] Q: "+c.getQuestion());
                if (ans==null) continue; boolean ok = c.checkAnswer(ans);
                String msg = ok?"Correct!":"Wrong. Ans: "+c.getAnswer();
                String qstr = JOptionPane.showInputDialog(this, msg+"
Rate quality (0-5) or leave empty to auto-rate:");
                int quality; if (qstr==null || qstr.trim().isEmpty()) quality = ok?5:2; else try{ quality = Integer.parseInt(qstr.trim()); } catch(Exception ex){ quality = ok?5:2; }
                c.updateSM2(quality);
            }
        }
        JOptionPane.showMessageDialog(this, "Review session done.");
        refreshCardTable();
    }

    private void onQuizSingleCard(){
        Deck sel = deckJList.getSelectedValue(); if (sel==null) return;
        int r = cardTable.getSelectedRow(); if (r<0) return; int idx = (int)cardTableModel.getValueAt(r,0)-1;
        Flashcard c = sel.getCards().get(idx);
        String ans = JOptionPane.showInputDialog(this, "Q: "+c.getQuestion()); if (ans==null) return; boolean ok = c.checkAnswer(ans);
        JOptionPane.showMessageDialog(this, ok?"Correct":"Wrong. Ans: "+c.getAnswer());
        String qstr = JOptionPane.showInputDialog(this, "Rate quality (0-5) or leave empty to auto-rate:");
        int quality; if (qstr==null || qstr.trim().isEmpty()) quality = ok?5:2; else try{ quality = Integer.parseInt(qstr.trim()); } catch(Exception ex){ quality = ok?5:2; }
        c.updateSM2(quality); refreshCardTable();
    }

    public static void main(String[] args){
        SwingUtilities.invokeLater(()->{
            FlashcardAppSwing app = new FlashcardAppSwing();
            app.setVisible(true);
        });
    }
}
