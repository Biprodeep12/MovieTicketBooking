import javax.swing.*;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

// ==========================================
// VIEW: Handles the Swing window (no SQL here)
// ==========================================
/*
 * The View is the window the user sees. It is built with Swing (Java's GUI library).
 * It only creates and arranges components - it never touches the database.
 * The buttons don't do anything by themselves; the Controller tells them what to do.
 *
 * At the top: the CineSeat title on the left, and "Hi, name" + Login/Logout on the right.
 *
 * The window has up to 3 tabs:
 *   1. Shows        -> has 2 pages that swap places:
 *                        "shows" page: one card per show, and "Your Bookings" below (when logged in)
 *                        "seats" page: 6 x 10 seat grid, total price, Continue button
 *   2. Manage Shows -> (admin only) a table of all shows + "Add Show" / "Delete Selected" buttons
 *   3. Bookings     -> (admin only) a table of all bookings + "Cancel Selected" button
 *
 * Every popup (login, add show, delete, confirm, messages) is built by the same
 * popup() method, so they all look and behave the same way.
 *
 * Fields (buttons, tables, labels) are not private, so the Controller can use them.
 */
public class BookingView extends JFrame { // "extends JFrame" = this class IS a window

    // Colours used across the app, written as Red, Green, Blue (0-255)
    static final Color WHITE = Color.WHITE, BG = new Color(246, 247, 249), LINE = new Color(222, 226, 230),
            TEXT = new Color(33, 37, 41), MUTED = new Color(150, 156, 163), ACCENT = new Color(220, 53, 69),
            DARK = new Color(52, 58, 64);
    static final Color SEAT = new Color(233, 236, 239); // available seat; booked seats are white
    static final int TOTAL_SEATS = 60; // 6 rows x 10 seats

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

        // white backgrounds, dark text, thin grey borders
        UIManager.put("Panel.background", WHITE);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TextField.border", border(6, 8));
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

    // ----- Top right: who is logged in, and the Login / Logout button -----
    JLabel userLabel = new JLabel("");
    JButton loginBtn = button("Login", ACCENT);

    // ----- The tabs -----
    JTabbedPane tabs = new JTabbedPane();
    JPanel showsTab, bookingsTab; // the 2 admin tabs (built in the constructor)

    // ----- Tab 1: Shows -----
    // CardLayout stacks panels on top of each other and shows one at a time,
    // like a deck of cards. We use it to switch between the "shows" and "seats" pages.
    CardLayout pages = new CardLayout();
    JPanel bookTab = new JPanel(pages);
    JPanel cards = new JPanel(new GridLayout(0, 3, 18, 18)); // show cards, 3 per row
    JLabel showLabel = new JLabel("", JLabel.CENTER); // the chosen show, above the seats
    // A JToggleButton stays pressed in after a click (and pops out on the next click).
    // That's perfect for seats: pressed in = selected.
    JToggleButton[] seats = new JToggleButton[TOTAL_SEATS];
    JLabel totalLabel = new JLabel("Total: Rs. 0.00");
    JButton backBtn = button("< Back", DARK);
    JButton bookBtn = button("Continue", ACCENT);
    // "Your Bookings" section under the cards (only visible when logged in)
    JPanel myBookings = new JPanel(new BorderLayout(0, 10));
    JLabel myBookingsTitle = heading("Your Bookings");
    DefaultTableModel myBookingData = new DefaultTableModel(
            new String[] { "Movie", "Date", "Time", "Screen", "Seat", "Price (Rs.)" }, 0);
    JTable myBookingTable = table(myBookingData);

    // ----- Tab 2: Manage Shows (admin only) -----
    JButton addShowBtn = button("+ Add Show", ACCENT);
    JButton deleteShowBtn = button("Delete Selected", DARK);
    JLabel showsTitle = heading("All Shows");
    // A JTable shows the data; the DefaultTableModel holds the rows and column names
    DefaultTableModel showData = new DefaultTableModel(
            new String[] { "ID", "Movie", "Date", "Time", "Screen", "Price (Rs.)", "Seats Booked" }, 0);
    JTable showTable = table(showData);

    // ----- Tab 3: Bookings (admin only) -----
    JLabel bookingsTitle = heading("All Bookings");
    DefaultTableModel bookingData = new DefaultTableModel(
            new String[] { "ID", "Movie", "Date", "Time", "Seat", "Name", "Phone", "Price (Rs.)" }, 0);
    JTable bookingTable = table(bookingData);
    JButton cancelBtn = button("Cancel Selected", DARK);

    // Builds the whole window: puts the components above into panels and tabs.
    public BookingView() {
        super("CineSeat - Movie Ticket Booking"); // window title

        // --- Seat grid: 6 rows (A-F) x 10 seats = 60 buttons ---
        JPanel seatPanel = new JPanel(new GridLayout(6, 10, 6, 6));
        for (int i = 0; i < TOTAL_SEATS; i++) {
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

        // --- "shows" page: a heading, then the cards inside a scroll pane so many shows still fit ---
        JLabel nowShowing = heading("Now Showing");
        nowShowing.setFont(new Font("Segoe UI", Font.BOLD, 20));
        JLabel pickHint = new JLabel("Pick a show to choose your seats");
        pickHint.setForeground(MUTED);
        JPanel cardsHeader = new JPanel(new GridLayout(2, 1));
        cardsHeader.setBackground(BG);
        cardsHeader.add(nowShowing);
        cardsHeader.add(pickHint);
        cards.setBackground(BG); // light grey behind the white cards so they stand out
        JPanel cardsTop = new JPanel(new BorderLayout(0, 16));
        cardsTop.setBackground(BG);
        cardsTop.setBorder(BorderFactory.createEmptyBorder(18, 22, 22, 22));
        cardsTop.add(cardsHeader, BorderLayout.NORTH);
        cardsTop.add(cards, BorderLayout.CENTER);
        // "Your Bookings": heading, then the table. The table's header is added by hand
        // because the table isn't inside its own JScrollPane (the whole page scrolls instead).
        JPanel myTable = new JPanel(new BorderLayout());
        myTable.setBorder(border(0, 0));
        myTable.add(myBookingTable.getTableHeader(), BorderLayout.NORTH);
        myTable.add(myBookingTable, BorderLayout.CENTER);
        myBookings.setBackground(BG);
        myBookings.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        myBookings.add(myBookingsTitle, BorderLayout.NORTH);
        myBookings.add(myTable, BorderLayout.CENTER);
        myBookings.setVisible(false); // hidden until someone logs in
        cardsTop.add(myBookings, BorderLayout.SOUTH);
        JPanel cardsPage = new JPanel(new BorderLayout());
        cardsPage.add(cardsTop, BorderLayout.NORTH); // NORTH keeps the cards at the top instead of stretched
        cardsPage.setBackground(BG);
        JScrollPane cardScroll = new JScrollPane(cardsPage);
        cardScroll.setBorder(null);
        cardScroll.getVerticalScrollBar().setUnitIncrement(16); // smoother mouse-wheel scrolling
        // add both pages to the CardLayout; the names are used later by showPage("...")
        bookTab.add(cardScroll, "shows");
        bookTab.add(layout(seatTop, hall, row(totalLabel, bookBtn)), "seats");

        // --- Big title at the top-left of the window ---
        JLabel title = new JLabel("CineSeat");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));

        // --- Logo: shown next to the title and used as the window/taskbar icon ---
        title.setIcon(new Logo(44));
        title.setIconTextGap(10); // space between the logo and the text
        // the window icon must be a picture, so draw the logo onto a blank 256 x 256 image
        BufferedImage icon = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        new Logo(256).paintIcon(null, icon.getGraphics(), 0, 0);
        setIconImage(icon);

        // --- Header: title on the left, "Hi, name" + Login button on the right ---
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(12, 20, 10, 20));
        header.add(title, BorderLayout.WEST);
        header.add(row(userLabel, loginBtn), BorderLayout.EAST);

        // --- Tabs: only "Shows" at the start. setUser() adds the other 2 for the admin. ---
        showsTab = tableSection(showsTitle, showTable, deleteShowBtn, addShowBtn);
        bookingsTab = tableSection(bookingsTitle, bookingTable, cancelBtn, null);
        tabs.addTab("Shows", bookTab);
        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        setDefaultCloseOperation(EXIT_ON_CLOSE); // closing the window ends the program
        setSize(1050, 680);
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
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); // pointing hand on hover
        return b;
    }

    // A small coloured label with a border (used for the colour key, SCREEN bar and popup summaries)
    private static JLabel chip(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setOpaque(true); // labels are see-through by default; this lets the background colour show
        l.setBackground(color);
        l.setForeground(color == ACCENT ? WHITE : TEXT); // white text on red, dark text otherwise
        l.setBorder(border(5, 12));
        return l;
    }

    // The grey summary box at the top of a popup:
    //   a big bold title, then one line per item in "lines", then an optional red line.
    // Swing labels understand simple HTML, which lets us use sizes, colours and line breaks (<br>).
    // The 300px width makes long text wrap onto the next line instead of making the popup huge.
    private static JLabel summary(String title, String redLine, String... lines) {
        StringBuilder html = new StringBuilder("<html><div style='width:300px'><font size=5><b>" + title
                + "</b></font>");
        if (lines.length > 0)
            html.append("<br>");
        for (String line : lines)
            html.append("<br>").append(line);
        if (redLine != null)
            html.append("<br><br><font color=#dc3545><b>").append(redLine).append("</b></font>");
        JLabel box = chip(html + "</div></html>", BG);
        box.setBorder(border(14, 16));
        return box;
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

    // Builds a section: heading on the left, buttons on the right, table below.
    // "rowButton" starts disabled and only becomes clickable when a row is selected,
    // so the user can't click "Delete" without choosing what to delete.
    // "otherButton" (can be null) is always clickable, e.g. "+ Add Show".
    private static JPanel tableSection(JLabel title, JTable table, JButton rowButton, JButton otherButton) {
        rowButton.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(e -> rowButton.setEnabled(table.getSelectedRow() >= 0));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(rowButton);
        if (otherButton != null)
            buttons.add(otherButton);
        JPanel top = new JPanel(new BorderLayout());
        top.add(title, BorderLayout.WEST);
        top.add(buttons, BorderLayout.EAST);
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

    // ----- Popups -----
    // All popups are built the same way, so they look alike:
    //
    //   +------------------------------------+
    //   | Title                              |
    //   | +--------------------------------+ |
    //   | | grey summary box               | |
    //   | +--------------------------------+ |
    //   |  form fields (only in some popups) |
    //   |  red error line (if something's wrong)
    //   |               [Cancel] [Confirm]   |
    //   +------------------------------------+

    // Builds (but doesn't open) a popup with a heading, a body, an error line and buttons on the right.
    // The LAST button is the main one: pressing Enter clicks it.
    // "true" in new JDialog(...) makes it modal: the user must close it before using the main window.
    private JDialog popup(String title, Component body, JLabel error, JButton... buttons) {
        JDialog dialog = new JDialog(this, title, true);
        JLabel head = heading(title);
        head.setFont(new Font("Segoe UI", Font.BOLD, 18));
        JPanel bottom = new JPanel(new BorderLayout(0, 10));
        if (error != null)
            bottom.add(error, BorderLayout.NORTH);
        bottom.add(fill(new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)), (Object[]) buttons),
                BorderLayout.SOUTH);
        JPanel content = layout(head, body, bottom);
        content.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(buttons[buttons.length - 1]); // Enter = main button
        dialog.setResizable(false);
        dialog.pack(); // make the popup just big enough for its contents
        dialog.setLocationRelativeTo(this); // centre it over the main window
        return dialog;
    }

    // A label above a text box, e.g. "Full Name" over an empty box
    private static JPanel field(String label, JTextField box) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        JLabel l = new JLabel(label);
        l.setForeground(MUTED);
        p.add(l, BorderLayout.NORTH);
        p.add(box, BorderLayout.CENTER);
        return p;
    }

    private String[] answer; // what the user typed in the last form popup (null = cancelled)

    // Opens a popup with a summary box and some text boxes ("fields"), laid out in "columns" columns.
    // When the main button is clicked:
    //   - everything typed is passed to onConfirm (a method from the Controller)
    //   - onConfirm checks it and saves it. It returns null if all went well,
    //     or an error message, e.g. "Phone number must be 10 digits."
    //   - on an error the popup STAYS OPEN and shows the message in red, so the user can fix it
    //   - with no error the popup closes
    // Returns what was typed, or null if the user clicked Cancel or closed the popup.
    private String[] askForm(String title, JLabel summary, String confirmText, int columns,
            String[] labels, JTextField[] boxes, Function<String[], String> onConfirm) {
        JPanel form = new JPanel(new GridLayout(0, columns, 12, 10));
        for (int i = 0; i < boxes.length; i++)
            form.add(field(labels[i], boxes[i]));
        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.add(summary, BorderLayout.NORTH);
        body.add(form, BorderLayout.CENTER);

        JLabel error = new JLabel(" "); // a space keeps room for the message, so the popup doesn't jump
        error.setForeground(ACCENT);
        JButton cancel = button("Cancel", DARK), confirm = button(confirmText, ACCENT);
        JDialog dialog = popup(title, body, error, cancel, confirm);

        answer = null;
        cancel.addActionListener(e -> dialog.dispose()); // close the popup
        confirm.addActionListener(e -> {
            String[] typed = new String[boxes.length];
            for (int i = 0; i < boxes.length; i++) // trim() removes spaces at the start/end
                typed[i] = boxes[i] instanceof JPasswordField // a password box hides its text,
                        ? new String(((JPasswordField) boxes[i]).getPassword()) // so it is read differently
                        : boxes[i].getText().trim();
            String problem = onConfirm.apply(typed);
            if (problem == null) {
                answer = typed;
                dialog.dispose();
            } else
                error.setText(problem);
        });
        dialog.setVisible(true); // the code pauses on this line until the popup is closed
        return answer;
    }

    // ----- Methods the Controller calls -----

    // Creates one card for each show (see the ShowCard class at the bottom of this file).
    // "onClick" is the method to run when a card is clicked - the Controller passes in openShow,
    // so clicking a card calls openShow(thatShow).
    public void fillCards(ArrayList<String[]> shows, Consumer<String[]> onClick) {
        cards.removeAll(); // remove the old cards first
        for (String[] s : shows)
            cards.add(new ShowCard(s, () -> onClick.accept(s)));
        if (shows.isEmpty()) {
            JLabel empty = new JLabel("No shows yet. The admin can add one from the Manage Shows tab.");
            empty.setForeground(MUTED);
            cards.add(empty);
        }
        // tell Swing the panel changed, so it redraws the new cards
        cards.revalidate();
        cards.repaint();
    }

    // Switches the Book Tickets tab to a page: "shows" (the cards) or "seats" (the seat grid)
    public void showPage(String name) {
        pages.show(bookTab, name);
    }

    // Updates the screen after logging in or out.
    //   name  = what to show after "Hi, " (null = nobody is logged in)
    //   admin = true only for the admin, who also gets the "Manage Shows" and "Bookings" tabs
    public void setUser(String name, boolean admin) {
        userLabel.setText(name == null ? "" : "Hi, " + name);
        loginBtn.setText(name == null ? "Login" : "Logout");
        myBookings.setVisible(name != null && !admin); // the admin sees all bookings in the Bookings tab
        while (tabs.getTabCount() > 1) // remove every tab except the first one ("Shows")
            tabs.removeTabAt(1);
        if (admin) {
            tabs.addTab("Manage Shows", showsTab);
            tabs.addTab("Bookings", bookingsTab);
        }
    }

    // Login popup: Phone and Password.
    // Returns {phone, password}, or null if cancelled.
    public String[] askLogin(Function<String[], String> onConfirm) {
        return askForm("Login", summary("Welcome to CineSeat", null, "New here? Just enter your phone number",
                "and a password - your account is created automatically."), "Login", 1,
                new String[] { "Phone Number (10 digits)", "Password" },
                new JTextField[] { new JTextField(24), new JPasswordField(24) }, onConfirm);
    }

    // Booking popup: summary of the show, seats and total, then a Name box.
    // Returns {name}, or null if cancelled.
    public String[] askName(String movie, String total, String when, String seats,
            Function<String[], String> onConfirm) {
        return askForm("Confirm Booking", summary(movie, total, when, seats), "Confirm Booking", 1,
                new String[] { "Name for this booking" }, new JTextField[] { new JTextField(24) }, onConfirm);
    }

    // "Add a New Show" popup: 5 boxes in 2 columns (some start with example values).
    // Returns {movie, date, time, screen, price}, or null if cancelled.
    public String[] askShow(Function<String[], String> onConfirm) {
        return askForm("Add a New Show",
                summary("New Show", null, "It will appear as a card on the Shows tab.",
                        "Rows C-D cost x1.25 and E-F x1.5 of the base price."),
                "+ Add Show", 2,
                new String[] { "Movie", "Screen", "Date (YYYY-MM-DD)", "Time (HH:MM)", "Base Price (Rs.)" },
                new JTextField[] { new JTextField(16), new JTextField("Screen 1"),
                        new JTextField(LocalDate.now().toString()), new JTextField("18:00"),
                        new JTextField("150") },
                onConfirm);
    }

    // Asks "are you sure?" with a summary of what will happen.
    // Returns true if the user clicked the red confirm button.
    public boolean askConfirm(String title, String confirmText, String redLine, String heading,
            String... lines) {
        JButton keep = button("Go Back", DARK), yes = button(confirmText, ACCENT);
        JDialog dialog = popup(title, summary(heading, redLine, lines), null, keep, yes);
        boolean[] clicked = { false }; // an array, because a lambda can't change a normal local variable
        keep.addActionListener(e -> dialog.dispose());
        yes.addActionListener(e -> {
            clicked[0] = true;
            dialog.dispose();
        });
        dialog.setVisible(true);
        return clicked[0];
    }

    // Shows a message popup with an OK button, e.g. showMessage("Booking Confirmed", "Inception", "Seats: A1")
    public void showMessage(String title, String heading, String... lines) {
        JButton ok = button("OK", ACCENT);
        JDialog dialog = popup(title, summary(heading, null, lines), null, ok);
        ok.addActionListener(e -> dialog.dispose());
        dialog.setVisible(true);
    }

    // Replaces all rows in a table with new ones
    public void fillTable(DefaultTableModel table, ArrayList<String[]> rows) {
        table.setRowCount(0); // remove the old rows
        for (String[] row : rows)
            table.addRow(row);
    }

    // Turns "2026-11-05" into "Thu, 5 Nov". If the text isn't a valid date, it is left as it is.
    static String niceDate(String date) {
        try {
            return LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH));
        } catch (Exception e) {
            return date;
        }
    }

    // Turns "18:00" into "6:00 PM"
    static String niceTime(String time) {
        try {
            return LocalTime.parse(time).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
        } catch (Exception e) {
            return time;
        }
    }

    // ==========================================
    // Logo: a red ticket with a white play button
    // ==========================================
    // Drawn with shapes instead of loaded from a picture file.
    // A picture gets blurry when it is resized; shapes are redrawn sharp at any size.
    // We draw on a 100 x 100 grid, and g.scale(...) stretches it to the real size.
    static class Logo implements Icon {
        final int size;

        Logo(int size) {
            this.size = size;
        }

        public int getIconWidth() {
            return size;
        }

        public int getIconHeight() {
            return size;
        }

        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // smooth edges
            g.translate(x, y);
            g.scale(size / 100.0, size / 100.0);
            // ticket: a red rounded rectangle with a round notch cut out of the left and right sides
            Area ticket = new Area(new RoundRectangle2D.Double(4, 20, 92, 60, 16, 16));
            ticket.subtract(new Area(new Ellipse2D.Double(-5, 41, 18, 18)));
            ticket.subtract(new Area(new Ellipse2D.Double(87, 41, 18, 18)));
            g.setColor(ACCENT);
            g.fill(ticket);
            // tear-off line: a column of small white dots
            g.setColor(new Color(255, 255, 255, 170));
            for (int dotY = 29; dotY <= 68; dotY += 9)
                g.fillOval(70, dotY, 4, 4);
            // play button: a white triangle with rounded corners
            Polygon play = new Polygon(new int[] { 33, 33, 57 }, new int[] { 37, 63, 50 }, 3);
            g.setColor(WHITE);
            g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.fill(play);
            g.draw(play);
            g.dispose();
        }
    }

    // ==========================================
    // ShowCard: one show on the Shows page
    // ==========================================
    /*
     * Instead of using labels and buttons, the card draws itself in paintComponent()
     * using Graphics2D (shapes, colours and text at x, y positions). (0, 0) is the top-left corner.
     *
     *   +--------------------------+
     *   | [SCREEN 1]               |  <- coloured "poster" (colour picked from the movie name)
     *   | Inception             I  |
     *   |--------------------------|
     *   | Thu, 5 Nov  -  10:00 AM  |
     *   | 52 of 60 seats left      |
     *   |--------------------------|
     *   | From Rs. 180    [Book ->]|
     *   +--------------------------+
     *
     * Moving the mouse over it lifts it up with a shadow and a red outline.
     * A sold-out show can't be clicked.
     */
    static class ShowCard extends JPanel {
        // Poster colours. Each movie always gets the same one (picked from its name).
        static final Color[] POSTERS = { new Color(220, 53, 69), new Color(79, 70, 229), new Color(13, 148, 136),
                new Color(217, 119, 6), new Color(147, 51, 234), new Color(37, 99, 235) };
        static final int POSTER_HEIGHT = 100, ROUND = 16;

        final String movie, screen, when, price;
        final int seatsLeft;
        boolean hover; // true while the mouse is over the card

        // s = {id, movie, date, time, screen, price, seatsBooked}
        ShowCard(String[] s, Runnable onClick) {
            movie = s[1];
            screen = s[4].toUpperCase();
            when = niceDate(s[2]) + "  \u2022  " + niceTime(s[3]); // \u2022 is a dot
            price = String.format("From Rs. %.0f", Double.parseDouble(s[5]));
            seatsLeft = TOTAL_SEATS - Integer.parseInt(s[6]);
            setOpaque(false); // we draw our own rounded background, so the corners stay see-through
            setPreferredSize(new Dimension(280, 226));
            if (seatsLeft == 0)
                return; // sold out: no hover effect, no click
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint(); // draw the card again, now with the hover look
                }

                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                public void mouseClicked(MouseEvent e) {
                    onClick.run(); // tell the Controller this show was clicked
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create(); // a copy, so our changes don't leak out
            // smooth edges on shapes and text
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth() - 1, h = getHeight() - 6;
            int top = hover ? 0 : 3; // on hover the card moves up 3 pixels
            RoundRectangle2D shape = new RoundRectangle2D.Float(0, top, w, h, ROUND, ROUND);

            // soft shadow under the card when hovered
            if (hover) {
                g.setColor(new Color(0, 0, 0, 22)); // the 4th number is transparency (0-255)
                g.fill(new RoundRectangle2D.Float(2, top + 5, w - 2, h, ROUND, ROUND));
            }
            g.setColor(WHITE);
            g.fill(shape);

            // --- poster: a colour gradient, cut to the card's rounded top corners ---
            Shape oldClip = g.getClip();
            g.clip(shape); // anything drawn now only shows inside the rounded card
            Color c = seatsLeft == 0 ? MUTED : POSTERS[Math.abs(movie.hashCode()) % POSTERS.length];
            g.setPaint(new GradientPaint(0, top, c, w, top + POSTER_HEIGHT, c.darker()));
            g.fillRect(0, top, w + 1, POSTER_HEIGHT);
            g.setColor(new Color(255, 255, 255, 45)); // big see-through first letter as decoration
            g.setFont(new Font("Segoe UI", Font.BOLD, 120));
            g.drawString(movie.isEmpty() ? "" : movie.substring(0, 1).toUpperCase(), w - 84, top + 112);
            g.setClip(oldClip);

            pill(g, screen, 14, top + 14, new Color(255, 255, 255, 60), WHITE, 11);
            g.setColor(WHITE);
            g.setFont(new Font("Segoe UI", Font.BOLD, 21));
            g.drawString(shorten(g, movie, w - 28), 14, top + 84);

            // --- date and time ---
            int y = top + POSTER_HEIGHT; // where the white part starts
            g.setColor(TEXT);
            g.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g.drawString(when, 16, y + 30);

            // --- seats left ---
            g.setColor(MUTED);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            String seatText = seatsLeft == 0 ? "No seats left"
                    : seatsLeft <= 5 ? "Hurry! Only " + seatsLeft + " seats left"
                            : seatsLeft + " of " + TOTAL_SEATS + " seats left";
            g.drawString(seatText, 16, y + 52);

            // --- divider line, then price on the left and the Book button on the right ---
            g.setColor(LINE);
            g.drawLine(16, y + 66, w - 16, y + 66);
            g.setColor(seatsLeft == 0 ? MUTED : ACCENT);
            g.setFont(new Font("Segoe UI", Font.BOLD, 16));
            g.drawString(price, 16, y + 98);
            if (seatsLeft == 0)
                pillRight(g, "SOLD OUT", w - 16, y + 78, SEAT, MUTED);
            else
                pillRight(g, "Book  \u2192", w - 16, y + 78, hover ? ACCENT.darker() : ACCENT, WHITE); // \u2192 is an arrow

            // --- outline: red on hover, grey otherwise ---
            g.setColor(hover ? ACCENT : LINE);
            g.draw(shape);
            g.dispose();
        }

        // Draws text inside a rounded "pill" whose top-left corner is at (x, y)
        private static void pill(Graphics2D g, String text, int x, int y, Color bg, Color fg, int size) {
            g.setFont(new Font("Segoe UI", Font.BOLD, size));
            FontMetrics fm = g.getFontMetrics(); // measures how wide/tall the text is
            int pw = fm.stringWidth(text) + 20, ph = fm.getHeight() + 8;
            g.setColor(bg);
            g.fillRoundRect(x, y, pw, ph, ph, ph);
            g.setColor(fg);
            g.drawString(text, x + 10, y + 4 + fm.getAscent());
        }

        // Same as pill(), but "right" is where the pill ends (used for the Book button)
        private static void pillRight(Graphics2D g, String text, int right, int y, Color bg, Color fg) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 13));
            pill(g, text, right - g.getFontMetrics().stringWidth(text) - 20, y, bg, fg, 13);
        }

        // Cuts long text to fit "maxWidth" pixels and adds "..." at the end
        private static String shorten(Graphics2D g, String text, int maxWidth) {
            FontMetrics fm = g.getFontMetrics();
            if (fm.stringWidth(text) <= maxWidth)
                return text;
            while (text.length() > 1 && fm.stringWidth(text + "...") > maxWidth)
                text = text.substring(0, text.length() - 1);
            return text + "...";
        }
    }
}
