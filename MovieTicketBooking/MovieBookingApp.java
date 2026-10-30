import javax.swing.*;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.function.Consumer;

public class MovieBookingApp {
    public static void main(String[] args) {
        BookingView.setTheme();
        new BookingController(new BookingModel(), new BookingView()).startApp();
    }
}

// ==========================================
// MODEL: Handles database connection and SQL operations
// ==========================================
class BookingModel {
    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:movie_booking.db");
    }

    // Runs an INSERT / DELETE / CREATE with ? parameters
    private void exec(String sql, Object... args) throws SQLException {
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++)
                ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
    }

    // Runs a SELECT and returns every row as a String[]
    private ArrayList<String[]> query(String sql, Object... args) throws SQLException {
        ArrayList<String[]> list = new ArrayList<>();
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++)
                ps.setObject(i + 1, args[i]);
            ResultSet rs = ps.executeQuery();
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                String[] row = new String[cols];
                for (int c = 0; c < cols; c++)
                    row[c] = rs.getString(c + 1);
                list.add(row);
            }
        }
        return list;
    }

    public void createTables() throws SQLException {
        // a screen can have only one show at a date and time
        exec("CREATE TABLE IF NOT EXISTS shows (id INTEGER PRIMARY KEY, movie TEXT, show_date TEXT, "
                + "show_time TEXT, screen TEXT, price REAL, UNIQUE(screen, show_date, show_time))");
        // a seat can be booked only once for a show
        exec("CREATE TABLE IF NOT EXISTS bookings (id INTEGER PRIMARY KEY, show_id INTEGER, seat TEXT, "
                + "name TEXT, phone TEXT, price REAL, UNIQUE(show_id, seat))");
        if (getShows().isEmpty())
            exec("INSERT INTO shows(movie, show_date, show_time, screen, price) VALUES "
                    + "('Inception', '2026-11-05', '10:00', 'Screen 1', 180), "
                    + "('Interstellar', '2026-11-05', '14:00', 'Screen 1', 200), "
                    + "('3 Idiots', '2026-11-05', '18:00', 'Screen 2', 150)");
    }

    public ArrayList<String[]> getShows() throws SQLException {
        return query("SELECT id, movie, show_date, show_time, screen, price FROM shows ORDER BY show_date, show_time");
    }

    public ArrayList<String[]> getBookings() throws SQLException {
        return query("SELECT b.id, s.movie, s.show_date, s.show_time, b.seat, b.name, b.phone, b.price "
                + "FROM bookings b JOIN shows s ON s.id = b.show_id ORDER BY b.id DESC");
    }

    public ArrayList<String> getBookedSeats(int showId) throws SQLException {
        ArrayList<String> seats = new ArrayList<>();
        for (String[] row : query("SELECT seat FROM bookings WHERE show_id = ?", showId))
            seats.add(row[0]);
        return seats;
    }

    public void addShow(String movie, String date, String time, String screen, double price) throws SQLException {
        exec("INSERT INTO shows(movie, show_date, show_time, screen, price) VALUES (?, ?, ?, ?, ?)",
                movie, date, time, screen, price);
    }

    public void deleteShow(int showId) throws SQLException {
        exec("DELETE FROM bookings WHERE show_id = ?", showId);
        exec("DELETE FROM shows WHERE id = ?", showId);
    }

    public void bookSeat(int showId, String seat, String name, String phone, double price) throws SQLException {
        exec("INSERT INTO bookings(show_id, seat, name, phone, price) VALUES (?, ?, ?, ?, ?)",
                showId, seat, name, phone, price);
    }

    public void cancelBooking(int bookingId) throws SQLException {
        exec("DELETE FROM bookings WHERE id = ?", bookingId);
    }

    // Ticket pricing: rows A-B normal price, C-D 1.25 times, E-F 1.5 times
    public double seatPrice(double basePrice, String seat) {
        char row = seat.charAt(0);
        return row <= 'B' ? basePrice : row <= 'D' ? basePrice * 1.25 : basePrice * 1.5;
    }
}

// ==========================================
// VIEW: Handles the Swing window (no SQL here)
// ==========================================
class BookingView extends JFrame {
    static final Color WHITE = Color.WHITE, BG = new Color(246, 247, 249), LINE = new Color(222, 226, 230),
            TEXT = new Color(33, 37, 41), MUTED = new Color(150, 156, 163), ACCENT = new Color(220, 53, 69),
            DARK = new Color(52, 58, 64);
    static final Color SEAT = new Color(233, 236, 239); // available seat; booked seats are white

    // Light theme: changes Swing's default fonts and colours.
    // Must be called before the window is created (see main).
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

    // Tab 1: Book Tickets has two pages: show cards -> seat selection
    CardLayout pages = new CardLayout();
    JPanel bookTab = new JPanel(pages);
    JPanel cards = new JPanel(new GridLayout(0, 3, 16, 16));
    JLabel showLabel = new JLabel("", JLabel.CENTER);
    JToggleButton[] seats = new JToggleButton[60];
    JLabel totalLabel = new JLabel("Total: Rs. 0.00");
    JButton backBtn = button("< Back", DARK);
    JButton bookBtn = button("Continue", ACCENT);

    // Tab 2: Shows
    JTextField movieField = new JTextField();
    JTextField dateField = new JTextField("2026-11-05");
    JTextField timeField = new JTextField("18:00");
    JTextField screenField = new JTextField("Screen 1");
    JTextField priceField = new JTextField("150");
    JButton addShowBtn = button("+ Add Show", ACCENT);
    JButton deleteShowBtn = button("Delete Selected", DARK);
    JLabel showsTitle = heading("All Shows");
    DefaultTableModel showData = new DefaultTableModel(
            new String[] { "ID", "Movie", "Date", "Time", "Screen", "Price (Rs.)" }, 0);
    JTable showTable = table(showData);

    // Tab 3: Bookings
    JLabel bookingsTitle = heading("All Bookings");
    DefaultTableModel bookingData = new DefaultTableModel(
            new String[] { "ID", "Movie", "Date", "Time", "Seat", "Name", "Phone", "Price (Rs.)" }, 0);
    JTable bookingTable = table(bookingData);
    JButton cancelBtn = button("Cancel Selected", DARK);

    public BookingView() {
        super("Movie Ticket Booking System");

        // 6 rows (A-F) x 10 seats
        JPanel seatPanel = new JPanel(new GridLayout(6, 10, 6, 6));
        for (int i = 0; i < 60; i++) {
            JToggleButton seat = seats[i] = new JToggleButton("" + (char) ('A' + i / 10) + (i % 10 + 1));
            seat.setBackground(SEAT);
            seat.setFocusPainted(false);
            // booked seats are disabled by the controller and turn white; selected seats
            // get white text
            seat.addPropertyChangeListener("enabled", e -> seat.setBackground(seat.isEnabled() ? SEAT : WHITE));
            seat.addItemListener(e -> seat.setForeground(seat.isSelected() ? WHITE : TEXT));
            seatPanel.add(seat);
        }

        JLabel screen = chip("SCREEN", BG);
        screen.setForeground(MUTED);
        screen.setHorizontalAlignment(JLabel.CENTER);
        JPanel hall = layout(screen, seatPanel, row(chip("Available", SEAT), chip("Selected", ACCENT),
                chip("Booked", WHITE), "    Price:  A-B normal,  C-D x1.25,  E-F x1.5"));
        hall.setBorder(BorderFactory.createEmptyBorder(0, 60, 0, 60));
        totalLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        showLabel.setFont(totalLabel.getFont());
        JPanel seatTop = new JPanel(new BorderLayout());
        seatTop.add(backBtn, BorderLayout.WEST);
        seatTop.add(showLabel, BorderLayout.CENTER);

        // show cards stay at the top and scroll when there are many
        JPanel cardsTop = new JPanel(new BorderLayout());
        cardsTop.add(cards, BorderLayout.NORTH);
        JScrollPane cardScroll = new JScrollPane(cardsTop);
        cardScroll.setBorder(null);
        bookTab.add(layout(row("Pick a show to book tickets"), cardScroll, null), "shows");
        bookTab.add(layout(seatTop, hall, row(totalLabel, bookBtn)), "seats");

        JLabel title = new JLabel("Movie Ticket Booking", JLabel.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setBorder(BorderFactory.createEmptyBorder(16, 0, 8, 0));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Book Tickets", bookTab);
        // "Add a New Show" box: labels on the first row, fields on the second
        JPanel form = fill(new JPanel(new GridLayout(2, 6, 10, 4)), "Movie", "Date (YYYY-MM-DD)", "Time (HH:MM)",
                "Screen", "Price (Rs.)", "", movieField, dateField, timeField, screenField, priceField, addShowBtn);
        form.setOpaque(false);
        JPanel addBox = layout(heading("Add a New Show"), form, null);
        addBox.setBackground(BG);
        addBox.setBorder(border(12, 16));

        tabs.addTab("Shows", layout(addBox, tableSection(showsTitle, deleteShowBtn, showTable), null));
        tabs.addTab("Bookings", layout(null, tableSection(bookingsTitle, cancelBtn, bookingTable), null));
        add(title, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1050, 640);
        setLocationRelativeTo(null);
    }

    // ----- small helpers to build styled components -----
    private static JButton button(String text, Color color) {
        JButton b = new JButton(text);
        b.setBackground(color);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        return b;
    }

    private static JLabel chip(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setOpaque(true);
        l.setBackground(color);
        l.setForeground(color == ACCENT ? WHITE : TEXT);
        l.setBorder(border(5, 12));
        return l;
    }

    // Thin grey outline with some padding inside
    private static BorderUIResource border(int vertical, int horizontal) {
        return new BorderUIResource(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal)));
    }

    private static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 16));
        return l;
    }

    private static JTable table(DefaultTableModel data) {
        JTable t = new JTable(data);
        t.setDefaultEditor(Object.class, null); // read-only
        t.setRowHeight(32);
        t.setShowVerticalLines(false);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setFillsViewportHeight(true);
        return t;
    }

    // Heading on the left, button on the right, table below.
    // The button only works when a row is selected.
    private static JPanel tableSection(JLabel title, JButton button, JTable table) {
        button.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(e -> button.setEnabled(table.getSelectedRow() >= 0));
        JPanel top = new JPanel(new BorderLayout());
        top.add(title, BorderLayout.WEST);
        top.add(button, BorderLayout.EAST);
        return layout(top, new JScrollPane(table), null);
    }

    // A centred row of components
    private static JPanel row(Object... parts) {
        return fill(new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8)), parts);
    }

    // Adds the parts to the panel; plain strings become labels
    private static JPanel fill(JPanel p, Object... parts) {
        for (Object o : parts)
            p.add(o instanceof String ? new JLabel((String) o) : (Component) o);
        return p;
    }

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

    // One clickable card per show (red strip on top, details, price); onClick gets
    // the clicked show
    public void fillCards(ArrayList<String[]> shows, Consumer<String[]> onClick) {
        cards.removeAll();
        for (String[] s : shows) {
            JButton card = button(String.format("<html><font size=5><b>%s</b></font><br><font color=#969ca3>%s"
                    + "&nbsp;&#8226;&nbsp;%s&nbsp;&#8226;&nbsp;%s</font><br><br><font color=#dc3545><b>From Rs. %.0f</b>"
                    + "</font>&nbsp;&nbsp;&nbsp;Book now &#8594;", s[1], s[2], s[3], s[4], Double.parseDouble(s[5])),
                    WHITE);
            card.setForeground(TEXT);
            card.setHorizontalAlignment(JButton.LEFT);
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            card.setRolloverEnabled(true);
            card.setBorder(cardBorder(LINE));
            // on hover the outline turns red and the card goes light grey
            card.getModel().addChangeListener(e -> {
                boolean hover = card.getModel().isRollover();
                card.setBackground(hover ? BG : WHITE);
                card.setBorder(cardBorder(hover ? ACCENT : LINE));
            });
            card.addActionListener(e -> onClick.accept(s));
            cards.add(card);
        }
        cards.revalidate();
        cards.repaint();
    }

    // Card outline in the given colour, a red strip on top, and padding inside
    private static javax.swing.border.Border cardBorder(Color outline) {
        return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(outline), BorderFactory
                .createCompoundBorder(BorderFactory.createMatteBorder(4, 0, 0, 0, ACCENT),
                        BorderFactory.createEmptyBorder(16, 20, 18, 20)));
    }

    public void showPage(String name) {
        pages.show(bookTab, name);
    }

    private boolean confirmed; // true when "Confirm Booking" was clicked in the popup

    // Popup with a booking summary and name/phone fields.
    // Returns { name, phone }, or null if the user cancelled.
    public String[] askCustomer(String show, String seats, String total) {
        JDialog dialog = new JDialog(this, "Your Details", true);
        JTextField name = new JTextField(22), phone = new JTextField(22);
        JButton cancel = button("Cancel", DARK), confirm = button("Confirm Booking", ACCENT);
        confirmed = false;
        confirm.addActionListener(e -> {
            confirmed = true;
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());

        JLabel summary = chip("<html><font size=5><b>" + show + "</b></font><br><br>Seats: " + seats
                + "<br><font color=#dc3545><b>" + total + "</b></font></html>", BG);
        JPanel form = fill(new JPanel(new GridLayout(0, 1, 0, 6)), "Full Name", name, "Phone Number (10 digits)",
                phone);
        JPanel content = layout(summary, form, row(cancel, confirm));
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(confirm); // Enter = confirm
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true); // the code waits on this line until the popup is closed
        if (!confirmed)
            return null;
        return new String[] { name.getText().trim(), phone.getText().trim() };
    }

    public void fillTable(DefaultTableModel table, ArrayList<String[]> rows) {
        table.setRowCount(0);
        for (String[] row : rows)
            table.addRow(row);
    }

    public void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    public boolean askYesNo(String message) {
        return JOptionPane.showConfirmDialog(this, message, "Confirm",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }
}

// ==========================================
// CONTROLLER: Coordinates between Model and View
// ==========================================
class BookingController {
    private final BookingModel model;
    private final BookingView view;
    private String[] show; // the show opened on the seat page

    public BookingController(BookingModel model, BookingView view) {
        this.model = model;
        this.view = view;
        for (JToggleButton seat : view.seats)
            seat.addActionListener(e -> updateTotal());
        view.backBtn.addActionListener(e -> view.showPage("shows"));
        view.bookBtn.addActionListener(e -> bookTickets());
        view.addShowBtn.addActionListener(e -> addShow());
        view.deleteShowBtn
                .addActionListener(e -> deleteSelected(view.showTable, "Delete this show and all its bookings?"));
        view.cancelBtn.addActionListener(e -> deleteSelected(view.bookingTable, "Cancel this booking?"));
    }

    public void startApp() {
        try {
            model.createTables();
            refresh();
        } catch (SQLException e) {
            view.showMessage("Database error: " + e.getMessage());
        }
        view.setVisible(true);
    }

    // Reload everything from the database and go back to the show cards
    private void refresh() throws SQLException {
        ArrayList<String[]> shows = model.getShows();
        ArrayList<String[]> bookings = model.getBookings();
        view.fillTable(view.showData, shows);
        view.fillTable(view.bookingData, bookings);
        view.showsTitle.setText("All Shows (" + shows.size() + ")");
        view.bookingsTitle.setText("All Bookings (" + bookings.size() + ")");
        view.fillCards(shows, this::openShow);
        view.showPage("shows");
    }

    // Card clicked: open the seat page with already booked seats disabled
    private void openShow(String[] s) {
        show = s;
        view.showLabel.setText(s[1] + "   |   " + s[2] + "  " + s[3] + "   |   " + s[4]);
        try {
            ArrayList<String> booked = model.getBookedSeats(Integer.parseInt(s[0]));
            for (JToggleButton seat : view.seats) {
                seat.setSelected(false);
                seat.setEnabled(!booked.contains(seat.getText()));
            }
            updateTotal();
            view.showPage("seats");
        } catch (SQLException e) {
            view.showMessage("Database error: " + e.getMessage());
        }
    }

    private ArrayList<String> getSelectedSeats() {
        ArrayList<String> list = new ArrayList<>();
        for (JToggleButton seat : view.seats)
            if (seat.isSelected())
                list.add(seat.getText());
        return list;
    }

    private void updateTotal() {
        double total = 0;
        for (String seat : getSelectedSeats())
            total += model.seatPrice(Double.parseDouble(show[5]), seat);
        view.totalLabel.setText(String.format("Total: Rs. %.2f", total));
    }

    // Continue clicked: ask name/phone in a popup, then save one booking per seat
    private void bookTickets() {
        ArrayList<String> seats = getSelectedSeats();
        if (seats.isEmpty()) {
            view.showMessage("Please select at least one seat.");
            return;
        }
        String[] customer = view.askCustomer(view.showLabel.getText(), String.join(", ", seats),
                view.totalLabel.getText());
        if (customer == null) // popup cancelled
            return;
        String name = customer[0], phone = customer[1];
        if (name.isEmpty()) {
            view.showMessage("Please enter your name.");
            return;
        }
        if (!phone.matches("[0-9]{10}")) {
            view.showMessage("Phone number must be 10 digits.");
            return;
        }
        try {
            for (String seat : seats)
                model.bookSeat(Integer.parseInt(show[0]), seat, name, phone,
                        model.seatPrice(Double.parseDouble(show[5]), seat));
            view.showMessage("Booked seats " + seats + " for " + name + ".\n" + view.totalLabel.getText());
            refresh();
        } catch (SQLException e) {
            view.showMessage("Could not book: " + e.getMessage());
        }
    }

    private void addShow() {
        String movie = view.movieField.getText().trim(), date = view.dateField.getText().trim();
        String time = view.timeField.getText().trim(), screen = view.screenField.getText().trim();
        String price = view.priceField.getText().trim();
        // check the inputs one by one; stop at the first problem
        String error = null;
        if (movie.isEmpty() || screen.isEmpty())
            error = "Please enter the movie name and screen.";
        else if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            error = "Date must be like 2026-11-05.";
        else if (!time.matches("[0-9]{2}:[0-9]{2}"))
            error = "Time must be like 18:00.";
        else if (!price.matches("[0-9]+(\\.[0-9]+)?") || Double.parseDouble(price) <= 0)
            error = "Price must be a number more than 0.";
        if (error != null) {
            view.showMessage(error);
            return;
        }
        try {
            model.addShow(movie, date, time, screen, Double.parseDouble(price));
            view.movieField.setText("");
            refresh();
        } catch (SQLException e) {
            view.showMessage(e.getMessage().contains("UNIQUE") ? "This screen already has a show at that date and time."
                    : "Could not add show: " + e.getMessage());
        }
    }

    // Deletes the selected show (with its bookings) or the selected booking
    private void deleteSelected(JTable table, String question) {
        int row = table.getSelectedRow(); // the button is only enabled when a row is selected
        if (!view.askYesNo(question))
            return;
        try {
            int id = Integer.parseInt(table.getValueAt(row, 0).toString());
            if (table == view.showTable)
                model.deleteShow(id);
            else
                model.cancelBooking(id);
            refresh();
        } catch (SQLException e) {
            view.showMessage("Database error: " + e.getMessage());
        }
    }
}
