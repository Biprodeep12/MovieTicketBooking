import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;

public class MovieBookingApp {
    public static void main(String[] args) {
        BookingModel model = new BookingModel();
        BookingView view = new BookingView();
        BookingController controller = new BookingController(model, view);
        controller.startApp();
    }
}

// ==========================================
// MODEL: Handles database connection and SQL operations
// ==========================================
// Start of Model
class BookingModel {
    private String url = "jdbc:sqlite:movie_booking.db";

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    public void createTables() throws SQLException {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            // a screen can have only one show at a date and time
            stmt.execute("CREATE TABLE IF NOT EXISTS shows (id INTEGER PRIMARY KEY, movie TEXT, show_date TEXT, "
                    + "show_time TEXT, screen TEXT, price REAL, UNIQUE(screen, show_date, show_time))");
            // a seat can be booked only once for a show
            stmt.execute("CREATE TABLE IF NOT EXISTS bookings (id INTEGER PRIMARY KEY, show_id INTEGER, seat TEXT, "
                    + "name TEXT, phone TEXT, price REAL, UNIQUE(show_id, seat))");

            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM shows");
            if (rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO shows(movie, show_date, show_time, screen, price) VALUES "
                        + "('Inception', '2026-11-05', '10:00', 'Screen 1', 180), "
                        + "('Interstellar', '2026-11-05', '14:00', 'Screen 1', 200), "
                        + "('3 Idiots', '2026-11-05', '18:00', 'Screen 2', 150)");
            }
        }
    }

    public ArrayList<String[]> getShows() throws SQLException {
        ArrayList<String[]> list = new ArrayList<>();
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM shows ORDER BY show_date, show_time");
            while (rs.next()) {
                list.add(new String[]{rs.getString("id"), rs.getString("movie"), rs.getString("show_date"),
                        rs.getString("show_time"), rs.getString("screen"), rs.getString("price")});
            }
        }
        return list;
    }

    public ArrayList<String[]> getBookings() throws SQLException {
        ArrayList<String[]> list = new ArrayList<>();
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT b.id, s.movie, s.show_date, s.show_time, b.seat, b.name, b.phone, b.price "
                    + "FROM bookings b JOIN shows s ON s.id = b.show_id ORDER BY b.id DESC");
            while (rs.next()) {
                list.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)});
            }
        }
        return list;
    }

    public ArrayList<String> getBookedSeats(int showId) throws SQLException {
        ArrayList<String> list = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement("SELECT seat FROM bookings WHERE show_id = ?")) {
            ps.setInt(1, showId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(rs.getString("seat"));
            }
        }
        return list;
    }

    public void addShow(String movie, String date, String time, String screen, double price) throws SQLException {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO shows(movie, show_date, show_time, screen, price) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, movie);
            ps.setString(2, date);
            ps.setString(3, time);
            ps.setString(4, screen);
            ps.setDouble(5, price);
            ps.executeUpdate();
        }
    }

    public void deleteShow(int showId) throws SQLException {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM bookings WHERE show_id = " + showId);
            stmt.executeUpdate("DELETE FROM shows WHERE id = " + showId);
        }
    }

    public void bookSeat(int showId, String seat, String name, String phone, double price) throws SQLException {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO bookings(show_id, seat, name, phone, price) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, showId);
            ps.setString(2, seat);
            ps.setString(3, name);
            ps.setString(4, phone);
            ps.setDouble(5, price);
            ps.executeUpdate();
        }
    }

    public void cancelBooking(int bookingId) throws SQLException {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM bookings WHERE id = " + bookingId);
        }
    }

    // Ticket pricing: rows A-B normal price, C-D 1.25 times, E-F 1.5 times
    public double seatPrice(double basePrice, String seat) {
        char row = seat.charAt(0);
        if (row == 'A' || row == 'B') return basePrice;
        if (row == 'C' || row == 'D') return basePrice * 1.25;
        return basePrice * 1.5;
    }
}
// End of model


// ==========================================
// VIEW: Handles the Swing window (no SQL here)
// ==========================================
// Start of View
class BookingView extends JFrame {
    // Tab 1: Book Tickets
    JComboBox<String> showBox = new JComboBox<>();
    JToggleButton[] seats = new JToggleButton[60];
    JTextField nameField = new JTextField(12);
    JTextField phoneField = new JTextField(10);
    JLabel totalLabel = new JLabel("Total: Rs. 0.0");
    JButton bookBtn = new JButton("Book");

    // Tab 2: Shows
    JTextField movieField = new JTextField(10);
    JTextField dateField = new JTextField("2026-11-05", 8);
    JTextField timeField = new JTextField("18:00", 4);
    JTextField screenField = new JTextField("Screen 1", 6);
    JTextField priceField = new JTextField("150", 4);
    JButton addShowBtn = new JButton("Add Show");
    JButton deleteShowBtn = new JButton("Delete Selected Show");
    DefaultTableModel showData = new DefaultTableModel(
            new String[]{"ID", "Movie", "Date", "Time", "Screen", "Price"}, 0);
    JTable showTable = new JTable(showData);

    // Tab 3: Bookings
    DefaultTableModel bookingData = new DefaultTableModel(
            new String[]{"ID", "Movie", "Date", "Time", "Seat", "Name", "Phone", "Price"}, 0);
    JTable bookingTable = new JTable(bookingData);
    JButton cancelBtn = new JButton("Cancel Selected Booking");

    public BookingView() {
        super("Movie Ticket Booking System");

        // ----- Tab 1: Book Tickets -----
        JPanel showPanel = new JPanel();
        showPanel.add(new JLabel("Select Show:"));
        showPanel.add(showBox);

        // 6 rows (A-F) x 10 seats
        JPanel seatPanel = new JPanel(new GridLayout(6, 10, 5, 5));
        for (int i = 0; i < 60; i++) {
            char row = (char) ('A' + i / 10);
            int number = i % 10 + 1;
            seats[i] = new JToggleButton("" + row + number);
            seatPanel.add(seats[i]);
        }

        JPanel hallPanel = new JPanel(new BorderLayout());
        hallPanel.add(new JLabel("----- SCREEN -----", JLabel.CENTER), BorderLayout.NORTH);
        hallPanel.add(seatPanel, BorderLayout.CENTER);
        hallPanel.add(new JLabel("Rows A-B: normal price, C-D: x1.25, E-F: x1.5.  Greyed seats are already booked.",
                JLabel.CENTER), BorderLayout.SOUTH);

        JPanel customerPanel = new JPanel();
        customerPanel.add(new JLabel("Name:"));
        customerPanel.add(nameField);
        customerPanel.add(new JLabel("Phone:"));
        customerPanel.add(phoneField);
        customerPanel.add(totalLabel);
        customerPanel.add(bookBtn);

        JPanel bookTab = new JPanel(new BorderLayout());
        bookTab.add(showPanel, BorderLayout.NORTH);
        bookTab.add(hallPanel, BorderLayout.CENTER);
        bookTab.add(customerPanel, BorderLayout.SOUTH);

        // ----- Tab 2: Shows -----
        JPanel addShowPanel = new JPanel();
        addShowPanel.add(new JLabel("Movie:"));
        addShowPanel.add(movieField);
        addShowPanel.add(new JLabel("Date:"));
        addShowPanel.add(dateField);
        addShowPanel.add(new JLabel("Time:"));
        addShowPanel.add(timeField);
        addShowPanel.add(new JLabel("Screen:"));
        addShowPanel.add(screenField);
        addShowPanel.add(new JLabel("Price:"));
        addShowPanel.add(priceField);
        addShowPanel.add(addShowBtn);

        JPanel deleteShowPanel = new JPanel();
        deleteShowPanel.add(deleteShowBtn);

        JPanel showsTab = new JPanel(new BorderLayout());
        showsTab.add(addShowPanel, BorderLayout.NORTH);
        showsTab.add(new JScrollPane(showTable), BorderLayout.CENTER);
        showsTab.add(deleteShowPanel, BorderLayout.SOUTH);

        // ----- Tab 3: Bookings -----
        JPanel cancelPanel = new JPanel();
        cancelPanel.add(cancelBtn);

        JPanel bookingsTab = new JPanel(new BorderLayout());
        bookingsTab.add(new JScrollPane(bookingTable), BorderLayout.CENTER);
        bookingsTab.add(cancelPanel, BorderLayout.SOUTH);

        // make the tables read-only
        showTable.setDefaultEditor(Object.class, null);
        bookingTable.setDefaultEditor(Object.class, null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Book Tickets", bookTab);
        tabs.addTab("Shows", showsTab);
        tabs.addTab("Bookings", bookingsTab);
        add(tabs);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(850, 500);
        setLocationRelativeTo(null);
    }

    public void fillTable(DefaultTableModel table, ArrayList<String[]> rows) {
        table.setRowCount(0);
        for (String[] row : rows) {
            table.addRow(row);
        }
    }

    public void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    public boolean askYesNo(String message) {
        int answer = JOptionPane.showConfirmDialog(this, message, "Confirm", JOptionPane.YES_NO_OPTION);
        return answer == JOptionPane.YES_OPTION;
    }
}
// End of view


// ==========================================
// CONTROLLER: Coordinates between Model and View
// ==========================================
// Start of controller
class BookingController {
    private BookingModel model;
    private BookingView view;
    private ArrayList<String[]> shows = new ArrayList<>(); // same order as view.showBox

    public BookingController(BookingModel model, BookingView view) {
        this.model = model;
        this.view = view;

        view.showBox.addActionListener(e -> loadSeats());
        for (int i = 0; i < view.seats.length; i++) {
            view.seats[i].addActionListener(e -> updateTotal());
        }
        view.bookBtn.addActionListener(e -> bookTickets());
        view.addShowBtn.addActionListener(e -> addShow());
        view.deleteShowBtn.addActionListener(e -> deleteShow());
        view.cancelBtn.addActionListener(e -> cancelBooking());
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

    // Reload everything from the database
    private void refresh() throws SQLException {
        int selected = view.showBox.getSelectedIndex();

        shows = model.getShows();
        view.fillTable(view.showData, shows);
        view.fillTable(view.bookingData, model.getBookings());

        view.showBox.removeAllItems();
        for (String[] s : shows) {
            view.showBox.addItem(s[1] + " | " + s[2] + " " + s[3] + " | " + s[4] + " | Rs. " + s[5]);
        }
        if (selected >= 0 && selected < shows.size()) {
            view.showBox.setSelectedIndex(selected);
        }
        loadSeats();
    }

    private String[] getSelectedShow() {
        int i = view.showBox.getSelectedIndex();
        if (i < 0 || i >= shows.size()) return null;
        return shows.get(i);
    }

    // Grey out the seats that are already booked for the selected show
    private void loadSeats() {
        String[] show = getSelectedShow();
        try {
            ArrayList<String> booked = new ArrayList<>();
            if (show != null) {
                booked = model.getBookedSeats(Integer.parseInt(show[0]));
            }
            for (JToggleButton seat : view.seats) {
                seat.setSelected(false);
                seat.setEnabled(show != null && !booked.contains(seat.getText()));
            }
        } catch (SQLException e) {
            view.showMessage("Database error: " + e.getMessage());
        }
        updateTotal();
    }

    private ArrayList<String> getSelectedSeats() {
        ArrayList<String> list = new ArrayList<>();
        for (JToggleButton seat : view.seats) {
            if (seat.isSelected()) list.add(seat.getText());
        }
        return list;
    }

    private void updateTotal() {
        String[] show = getSelectedShow();
        double total = 0;
        if (show != null) {
            double basePrice = Double.parseDouble(show[5]);
            for (String seat : getSelectedSeats()) {
                total += model.seatPrice(basePrice, seat);
            }
        }
        view.totalLabel.setText("Total: Rs. " + total);
    }

    private void bookTickets() {
        String[] show = getSelectedShow();
        ArrayList<String> seats = getSelectedSeats();
        String name = view.nameField.getText().trim();
        String phone = view.phoneField.getText().trim();

        if (show == null || seats.isEmpty()) {
            view.showMessage("Please select a show and at least one seat.");
            return;
        }
        if (name.isEmpty()) {
            view.showMessage("Please enter your name.");
            return;
        }
        if (!phone.matches("[0-9]{10}")) {
            view.showMessage("Phone number must be 10 digits.");
            return;
        }

        try {
            int showId = Integer.parseInt(show[0]);
            double basePrice = Double.parseDouble(show[5]);
            for (String seat : seats) {
                model.bookSeat(showId, seat, name, phone, model.seatPrice(basePrice, seat));
            }
            view.showMessage("Booked seats " + seats + " for " + name + ".\n" + view.totalLabel.getText());
            view.nameField.setText("");
            view.phoneField.setText("");
            refresh();
        } catch (SQLException e) {
            view.showMessage("Could not book: " + e.getMessage());
        }
    }

    private void addShow() {
        String movie = view.movieField.getText().trim();
        String date = view.dateField.getText().trim();
        String time = view.timeField.getText().trim();
        String screen = view.screenField.getText().trim();

        if (movie.isEmpty() || screen.isEmpty()) {
            view.showMessage("Please enter the movie name and screen.");
            return;
        }
        if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            view.showMessage("Date must be like 2026-11-05.");
            return;
        }
        if (!time.matches("[0-9]{2}:[0-9]{2}")) {
            view.showMessage("Time must be like 18:00.");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(view.priceField.getText().trim());
        } catch (NumberFormatException e) {
            view.showMessage("Price must be a number.");
            return;
        }
        if (price <= 0) {
            view.showMessage("Price must be more than 0.");
            return;
        }

        try {
            model.addShow(movie, date, time, screen, price);
            view.movieField.setText("");
            refresh();
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE")) {
                view.showMessage("This screen already has a show at that date and time.");
            } else {
                view.showMessage("Could not add show: " + e.getMessage());
            }
        }
    }

    private void deleteShow() {
        int row = view.showTable.getSelectedRow();
        if (row < 0) {
            view.showMessage("Please select a show in the table.");
            return;
        }
        if (!view.askYesNo("Delete this show and all its bookings?")) return;

        try {
            int showId = Integer.parseInt(view.showTable.getValueAt(row, 0).toString());
            model.deleteShow(showId);
            refresh();
        } catch (SQLException e) {
            view.showMessage("Could not delete: " + e.getMessage());
        }
    }

    private void cancelBooking() {
        int row = view.bookingTable.getSelectedRow();
        if (row < 0) {
            view.showMessage("Please select a booking in the table.");
            return;
        }
        if (!view.askYesNo("Cancel this booking?")) return;

        try {
            int bookingId = Integer.parseInt(view.bookingTable.getValueAt(row, 0).toString());
            model.cancelBooking(bookingId);
            refresh();
        } catch (SQLException e) {
            view.showMessage("Could not cancel: " + e.getMessage());
        }
    }
}
// End of controller
