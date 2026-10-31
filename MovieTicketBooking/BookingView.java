import javax.swing.*;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.function.Consumer;

// ==========================================
// VIEW: Handles the Swing window (no SQL here)
// ==========================================
/*
 * The View is the window the user sees. It is built with Swing (Java's GUI library).
 * It only creates and arranges components - it never touches the database.
 * The buttons don't do anything by themselves; the Controller tells them what to do.
 *
 * The window has 3 tabs:
 *   1. Book Tickets -> has 2 pages that swap places:
 *                        "shows" page: one card per show
 *                        "seats" page: 6 x 10 seat grid, total price, Continue button
 *   2. Shows        -> a form to add a show + a table of all shows
 *   3. Bookings     -> a table of all bookings
 *
 * Fields (buttons, tables, labels) are not private, so the Controller can use them.
 */
public class BookingView extends JFrame { // "extends JFrame" = this class IS a window

    // Colours used across the app, written as Red, Green, Blue (0-255)
    static final Color WHITE = Color.WHITE, BG = new Color(246, 247, 249), LINE = new Color(222, 226, 230),
            TEXT = new Color(33, 37, 41), MUTED = new Color(150, 156, 163), ACCENT = new Color(220, 53, 69),
            DARK = new Color(52, 58, 64);
    static final Color SEAT = new Color(233, 236, 239); // available seat; booked seats are white

    // Sets the look of the whole app (fonts and colours) in one place.
    // UIManager.put("Name", value) changes the default for every component of that type,
    // e.g. "Button.font" changes the font of every button.
    // Must run BEFORE the window is created (main() calls it first).
    static void setTheme() {
        Font plain = new FontUIResource("Segoe UI", Font.PLAIN, 14);
        Font bold = new FontUIResource("Segoe UI", Font.BOLD, 14);
        UIManager.put("Label.font", plain);
        UIManager.put("Button.font", plain);
        UIManager.put("TextField.font", plain);
        UIManager.put("TabbedPane.font", plain);
        UIManager.put("Table.font", plain);
        UIManager.put("TableHeader.font", bold);
        UIManager.put("ToggleButton.font", bold);
        UIManager.put("OptionPane.messageFont", plain);
        UIManager.put("OptionPane.buttonFont", plain);

        // white backgrounds, dark text, thin grey borders
        UIManager.put("Panel.background", WHITE);
        UIManager.put("OptionPane.background", WHITE);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TextField.border", border(5, 8));
        UIManager.put("ScrollPane.border", border(0, 0));

        // tabs: white selected tab, light grey others, grey lines
        UIManager.put("TabbedPane.selected", WHITE);
        UIManager.put("TabbedPane.unselectedBackground", BG);
        UIManager.put("TabbedPane.contentAreaColor", WHITE);
        UIManager.put("TabbedPane.highlight", LINE);
        UIManager.put("TabbedPane.darkShadow", LINE);
        UIManager.put("TabbedPane.selectHighlight", WHITE);
        UIManager.put("TabbedPane.focus", WHITE);
        UIManager.put("TabbedPane.tabInsets", new Insets(8, 22, 8, 22));

        // tables: grey lines and header, light red selected row
        UIManager.put("Table.scrollPaneBorder", border(0, 0));
        UIManager.put("Table.gridColor", LINE);
        UIManager.put("Table.selectionBackground", new Color(252, 228, 231));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("TableHeader.background", BG);
        UIManager.put("TableHeader.cellBorder", border(6, 8));

        // seats: red when selected, grey text when booked
        UIManager.put("ToggleButton.select", ACCENT);
        UIManager.put("ToggleButton.foreground", TEXT);
        UIManager.put("ToggleButton.disabledText", MUTED);
        UIManager.put("ToggleButton.border", border(0, 0));
    }

    // ----- Tab 1: Book Tickets -----
    // CardLayout stacks panels on top of each other and shows one at a time,
    // like a deck of cards. We use it to switch between the "shows" and "seats" pages.
    CardLayout pages = new CardLayout();
    JPanel bookTab = new JPanel(pages);
    JPanel cards = new JPanel(new GridLayout(0, 3, 16, 16)); // show cards, 3 per row
    JLabel showLabel = new JLabel("", JLabel.CENTER); // the chosen show, above the seats
    // A JToggleButton stays pressed in after a click (and pops out on the next click).
    // That's perfect for seats: pressed in = selected.
    JToggleButton[] seats = new JToggleButton[60];
    JLabel totalLabel = new JLabel("Total: Rs. 0.00");
    JButton backBtn = button("< Back", DARK);
    JButton bookBtn = button("Continue", ACCENT);

    // ----- Tab 2: Shows -----
    // the "Add a New Show" form (some fields start with example values)
    JTextField movieField = new JTextField();
    JTextField dateField = new JTextField("2026-11-05");
    JTextField timeField = new JTextField("18:00");
    JTextField screenField = new JTextField("Screen 1");
    JTextField priceField = new JTextField("150");
    JButton addShowBtn = button("+ Add Show", ACCENT);
    JButton deleteShowBtn = button("Delete Selected", DARK);
    JLabel showsTitle = heading("All Shows");
    // A JTable shows the data; the DefaultTableModel holds the rows and column names
    DefaultTableModel showData = new DefaultTableModel(
            new String[] { "ID", "Movie", "Date", "Time", "Screen", "Price (Rs.)" }, 0);
    JTable showTable = table(showData);

    // ----- Tab 3: Bookings -----
    JLabel bookingsTitle = heading("All Bookings");
    DefaultTableModel bookingData = new DefaultTableModel(
            new String[] { "ID", "Movie", "Date", "Time", "Seat", "Name", "Phone", "Price (Rs.)" }, 0);
    JTable bookingTable = table(bookingData);
    JButton cancelBtn = button("Cancel Selected", DARK);

    // Builds the whole window: puts the components above into panels and tabs.
    public BookingView() {
        super("Movie Ticket Booking System"); // window title

        // --- Seat grid: 6 rows (A-F) x 10 seats = 60 buttons ---
        JPanel seatPanel = new JPanel(new GridLayout(6, 10, 6, 6));
        for (int i = 0; i < 60; i++) {
            // Seat name: row letter + seat number. i / 10 gives the row (0 = A, 1 = B, ...),
            // i % 10 + 1 gives the number (1-10). So i = 0 -> "A1", i = 13 -> "B4".
            JToggleButton seat = seats[i] = new JToggleButton("" + (char) ('A' + i / 10) + (i % 10 + 1));
            seat.setBackground(SEAT);
            seat.setFocusPainted(false);
            // When the Controller disables a seat (already booked), turn it white
            seat.addPropertyChangeListener("enabled", e -> seat.setBackground(seat.isEnabled() ? SEAT : WHITE));
            // When a seat is selected it turns red (set in setTheme), so make its text white
            seat.addItemListener(e -> seat.setForeground(seat.isSelected() ? WHITE : TEXT));
            seatPanel.add(seat);
        }

        // --- "seats" page: SCREEN bar on top, seats in the middle, colour key at the bottom ---
        JLabel screen = chip("SCREEN", BG);
        screen.setForeground(MUTED);
        screen.setHorizontalAlignment(JLabel.CENTER);
        JPanel hall = layout(screen, seatPanel, row(chip("Available", SEAT), chip("Selected", ACCENT),
                chip("Booked", WHITE), "    Price:  A-B normal,  C-D x1.25,  E-F x1.5"));
        hall.setBorder(BorderFactory.createEmptyBorder(0, 60, 0, 60));
        totalLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        showLabel.setFont(totalLabel.getFont());
        JPanel seatTop = new JPanel(new BorderLayout()); // Back button on the left, show name in the middle
        seatTop.add(backBtn, BorderLayout.WEST);
        seatTop.add(showLabel, BorderLayout.CENTER);

        // --- "shows" page: the cards, inside a scroll pane so many shows still fit ---
        JPanel cardsTop = new JPanel(new BorderLayout());
        cardsTop.add(cards, BorderLayout.NORTH); // NORTH keeps the cards at the top instead of stretched
        JScrollPane cardScroll = new JScrollPane(cardsTop);
        cardScroll.setBorder(null);
        // add both pages to the CardLayout; the names are used later by showPage("...")
        bookTab.add(layout(row("Pick a show to book tickets"), cardScroll, null), "shows");
        bookTab.add(layout(seatTop, hall, row(totalLabel, bookBtn)), "seats");

        // --- Big title at the top of the window ---
        JLabel title = new JLabel("Movie Ticket Booking", JLabel.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setBorder(BorderFactory.createEmptyBorder(16, 0, 8, 0));

        // --- The 3 tabs ---
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Book Tickets", bookTab);
        // "Add a New Show" form: a 2 x 6 grid, labels on the first row, fields on the second
        JPanel form = fill(new JPanel(new GridLayout(2, 6, 10, 4)), "Movie", "Date (YYYY-MM-DD)", "Time (HH:MM)",
                "Screen", "Price (Rs.)", "", movieField, dateField, timeField, screenField, priceField, addShowBtn);
        form.setOpaque(false); // see-through, so the grey box behind it shows
        JPanel addBox = layout(heading("Add a New Show"), form, null);
        addBox.setBackground(BG);
        addBox.setBorder(border(12, 16));

        tabs.addTab("Shows", layout(addBox, tableSection(showsTitle, deleteShowBtn, showTable), null));
        tabs.addTab("Bookings", layout(null, tableSection(bookingsTitle, cancelBtn, bookingTable), null));
        add(title, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        setDefaultCloseOperation(EXIT_ON_CLOSE); // closing the window ends the program
        setSize(1050, 640);
        setLocationRelativeTo(null); // centre on the screen
    }

    // ----- Small helper methods that build styled components -----
    // They save us from repeating the same styling code many times.

    // A flat coloured button with white text, e.g. button("Continue", ACCENT)
    private static JButton button(String text, Color color) {
        JButton b = new JButton(text);
        b.setBackground(color);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false); // no dotted box around the text after clicking
        b.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18)); // padding: top, left, bottom, right
        return b;
    }

    // A small coloured label with a border (used for the colour key, SCREEN bar and booking summary)
    private static JLabel chip(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setOpaque(true); // labels are see-through by default; this lets the background colour show
        l.setBackground(color);
        l.setForeground(color == ACCENT ? WHITE : TEXT); // white text on red, dark text otherwise
        l.setBorder(border(5, 12));
        return l;
    }

    // A thin grey outline with some empty space (padding) inside it
    private static BorderUIResource border(int vertical, int horizontal) {
        return new BorderUIResource(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal)));
    }

    // A bold section title, e.g. "All Shows"
    private static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 16));
        return l;
    }

    // A table the user can look at and click, but not type into
    private static JTable table(DefaultTableModel data) {
        JTable t = new JTable(data);
        t.setDefaultEditor(Object.class, null); // no editor = cells can't be edited
        t.setRowHeight(32);
        t.setShowVerticalLines(false);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // only one row at a time
        t.setFillsViewportHeight(true);
        return t;
    }

    // Builds a section: heading on the left, button on the right, table below.
    // The button starts disabled and only becomes clickable when a row is selected,
    // so the user can't click "Delete" without choosing what to delete.
    private static JPanel tableSection(JLabel title, JButton button, JTable table) {
        button.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(e -> button.setEnabled(table.getSelectedRow() >= 0));
        JPanel top = new JPanel(new BorderLayout());
        top.add(title, BorderLayout.WEST);
        top.add(button, BorderLayout.EAST);
        return layout(top, new JScrollPane(table), null); // JScrollPane adds a scroll bar when needed
    }

    // Puts the given items side by side in a centred row
    private static JPanel row(Object... parts) {
        return fill(new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8)), parts);
    }

    // Adds each item to the panel. Plain text (a String) is turned into a label first.
    private static JPanel fill(JPanel p, Object... parts) {
        for (Object o : parts)
            p.add(o instanceof String ? new JLabel((String) o) : (Component) o);
        return p;
    }

    // A panel with up to 3 parts stacked top to bottom: north (top), center (middle), south (bottom).
    // Pass null to leave the top or bottom empty.
    private static JPanel layout(Component north, Component center, Component south) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        if (north != null)
            p.add(north, BorderLayout.NORTH);
        p.add(center, BorderLayout.CENTER);
        if (south != null)
            p.add(south, BorderLayout.SOUTH);
        return p;
    }

    // ----- Methods the Controller calls -----

    // Creates one clickable card (a big button) for each show.
    // "onClick" is the method to run when a card is clicked - the Controller passes in openShow,
    // so clicking a card calls openShow(thatShow).
    // Each show is {id, movie, date, time, screen, price}, so s[1] = movie ... s[5] = price.
    public void fillCards(ArrayList<String[]> shows, Consumer<String[]> onClick) {
        cards.removeAll(); // remove the old cards first
        for (String[] s : shows) {
            // Swing buttons understand simple HTML, which lets us use different sizes and colours
            JButton card = button(String.format("<html><font size=5><b>%s</b></font><br><font color=#969ca3>%s"
                    + "&nbsp;&#8226;&nbsp;%s&nbsp;&#8226;&nbsp;%s</font><br><br><font color=#dc3545><b>From Rs. %.0f</b>"
                    + "</font>&nbsp;&nbsp;&nbsp;Book now &#8594;", s[1], s[2], s[3], s[4], Double.parseDouble(s[5])),
                    WHITE);
            card.setForeground(TEXT);
            card.setHorizontalAlignment(JButton.LEFT);
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); // pointing hand on hover
            card.setRolloverEnabled(true); // lets us know when the mouse is over the card
            card.setBorder(cardBorder(LINE));
            // When the mouse moves over the card: red outline and light grey background
            card.getModel().addChangeListener(e -> {
                boolean hover = card.getModel().isRollover();
                card.setBackground(hover ? BG : WHITE);
                card.setBorder(cardBorder(hover ? ACCENT : LINE));
            });
            card.addActionListener(e -> onClick.accept(s)); // clicked -> tell the Controller which show
            cards.add(card);
        }
        // tell Swing the panel changed, so it redraws the new cards
        cards.revalidate();
        cards.repaint();
    }

    // The card's border: an outline in the given colour, a red strip on top, and padding inside
    private static javax.swing.border.Border cardBorder(Color outline) {
        return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(outline), BorderFactory
                .createCompoundBorder(BorderFactory.createMatteBorder(4, 0, 0, 0, ACCENT),
                        BorderFactory.createEmptyBorder(16, 20, 18, 20)));
    }

    // Switches the Book Tickets tab to a page: "shows" (the cards) or "seats" (the seat grid)
    public void showPage(String name) {
        pages.show(bookTab, name);
    }

    private boolean confirmed; // becomes true when "Confirm Booking" is clicked in the popup

    // Opens a popup with a booking summary and Name / Phone fields.
    // Returns {name, phone} if the user clicks "Confirm Booking",
    // or null if they click Cancel or close the popup.
    public String[] askCustomer(String show, String seats, String total) {
        // "true" makes it modal: the user must close the popup before using the main window
        JDialog dialog = new JDialog(this, "Your Details", true);
        JTextField name = new JTextField(22), phone = new JTextField(22);
        JButton cancel = button("Cancel", DARK), confirm = button("Confirm Booking", ACCENT);
        confirmed = false;
        confirm.addActionListener(e -> {
            confirmed = true;
            dialog.dispose(); // close the popup
        });
        cancel.addActionListener(e -> dialog.dispose());

        // Summary box: show name, seats and total price
        JLabel summary = chip("<html><font size=5><b>" + show + "</b></font><br><br>Seats: " + seats
                + "<br><font color=#dc3545><b>" + total + "</b></font></html>", BG);
        JPanel form = fill(new JPanel(new GridLayout(0, 1, 0, 6)), "Full Name", name, "Phone Number (10 digits)",
                phone);
        JPanel content = layout(summary, form, row(cancel, confirm));
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(confirm); // pressing Enter = clicking Confirm
        dialog.pack(); // make the popup just big enough for its contents
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true); // the code pauses on this line until the popup is closed
        if (!confirmed)
            return null;
        return new String[] { name.getText().trim(), phone.getText().trim() };
    }

    // Replaces all rows in a table with new ones
    public void fillTable(DefaultTableModel table, ArrayList<String[]> rows) {
        table.setRowCount(0); // remove the old rows
        for (String[] row : rows)
            table.addRow(row);
    }

    // Shows a simple message box with an OK button
    public void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    // Shows a Yes / No box. Returns true if the user clicked Yes.
    public boolean askYesNo(String message) {
        return JOptionPane.showConfirmDialog(this, message, "Confirm",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }
}
