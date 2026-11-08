import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;

// ==========================================
// MODEL: Handles database connection and SQL operations
// ==========================================
/*
 * The Model is the only part that talks to the database.
 * It has no buttons or windows - just SQL.
 *
 * The database is a single file called movie_booking.db (SQLite).
 * Java talks to it using JDBC (Java Database Connectivity).
 *
 * It has 3 tables:
 *   shows    -> one row per show:  id, movie, show_date, show_time, screen, price
 *   bookings -> one row per seat:  id, show_id, seat, name, phone, price
 *               (show_id says which show the seat belongs to, phone says whose booking it is)
 *   users    -> one row per user:  phone, password
 *
 * If something goes wrong (for example the seat is already taken), the method
 * "throws" an SQLException. The Controller catches it and shows a message.
 */
public class BookingModel {

    // Opens a connection to the database file.
    // If the file doesn't exist yet, SQLite creates it.
    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:movie_booking.db");
    }

    // Runs SQL that CHANGES data: INSERT, DELETE or CREATE TABLE.
    // Each ? in the SQL is filled with the next value from "args".
    // Example: exec("DELETE FROM shows WHERE id = ?", 5)  ->  deletes show 5
    // Using ? (a PreparedStatement) instead of joining strings keeps it safe from SQL injection.
    private void exec(String sql, Object... args) throws SQLException {
        // "try (...)" closes the connection automatically when done, even if there's an error
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++)
                ps.setObject(i + 1, args[i]); // the first ? is number 1, not 0
            ps.executeUpdate();
        }
    }

    // Runs a SELECT (READS data) and returns all rows.
    // Each row is a String[] with one item per column.
    // Example: "SELECT id, movie FROM shows"  ->  [ {"1", "Inception"}, {"2", "Interstellar"} ]
    private ArrayList<String[]> query(String sql, Object... args) throws SQLException {
        ArrayList<String[]> list = new ArrayList<>();
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++)
                ps.setObject(i + 1, args[i]);
            ResultSet rs = ps.executeQuery(); // the result, read one row at a time
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) { // rs.next() moves to the next row; false when there are no more
                String[] row = new String[cols];
                for (int c = 0; c < cols; c++)
                    row[c] = rs.getString(c + 1); // columns also start at 1
                list.add(row);
            }
        }
        return list;
    }

    // Creates the 3 tables the first time the app runs.
    // "IF NOT EXISTS" means nothing happens if they're already there.
    // If there are no shows yet, 3 sample shows for tomorrow are added so the app isn't empty.
    public void createTables() throws SQLException {
        // UNIQUE(screen, show_date, show_time): a screen can't have 2 shows at the same date and time
        exec("CREATE TABLE IF NOT EXISTS shows (id INTEGER PRIMARY KEY, movie TEXT, show_date TEXT, "
                + "show_time TEXT, screen TEXT, price REAL, UNIQUE(screen, show_date, show_time))");
        // UNIQUE(show_id, seat): the same seat can't be booked twice for one show
        exec("CREATE TABLE IF NOT EXISTS bookings (id INTEGER PRIMARY KEY, show_id INTEGER, seat TEXT, "
                + "name TEXT, phone TEXT, price REAL, UNIQUE(show_id, seat))");
        // one row per user account; the phone number is the PRIMARY KEY, so it can't be used twice
        exec("CREATE TABLE IF NOT EXISTS users (phone TEXT PRIMARY KEY, password TEXT)");
        if (getShows().isEmpty()) {
            String day = LocalDate.now().plusDays(1).toString(); // tomorrow, e.g. "2026-11-09"
            exec("INSERT INTO shows(movie, show_date, show_time, screen, price) VALUES "
                    + "('Inception', ?, '10:00', 'Screen 1', 180), "
                    + "('Interstellar', ?, '14:00', 'Screen 1', 200), "
                    + "('3 Idiots', ?, '18:00', 'Screen 2', 150)", day, day, day);
        }
    }

    // Returns every show, earliest first, with how many seats are already booked.
    // The (SELECT COUNT(*) ...) part counts the bookings that belong to each show.
    // Each row: {id, movie, date, time, screen, price, seatsBooked}
    public ArrayList<String[]> getShows() throws SQLException {
        return query("SELECT id, movie, show_date, show_time, screen, price, "
                + "(SELECT COUNT(*) FROM bookings b WHERE b.show_id = shows.id) "
                + "FROM shows ORDER BY show_date, show_time");
    }

    // Returns every booking, newest first.
    // A booking only stores show_id, so JOIN looks up that show to get its movie, date and time.
    // Each row: {id, movie, date, time, seat, name, phone, price}
    public ArrayList<String[]> getBookings() throws SQLException {
        return query("SELECT b.id, s.movie, s.show_date, s.show_time, b.seat, b.name, b.phone, b.price "
                + "FROM bookings b JOIN shows s ON s.id = b.show_id ORDER BY b.id DESC");
    }

    // Returns the bookings of ONE user (found by phone number), earliest show first.
    // Each row: {movie, date, time, screen, seat, price}
    public ArrayList<String[]> getUserBookings(String phone) throws SQLException {
        return query("SELECT s.movie, s.show_date, s.show_time, s.screen, b.seat, b.price "
                + "FROM bookings b JOIN shows s ON s.id = b.show_id WHERE b.phone = ? "
                + "ORDER BY s.show_date, s.show_time, b.seat", phone);
    }

    // Returns the saved password for a phone number, or null if there is no account for it yet.
    public String getPassword(String phone) throws SQLException {
        ArrayList<String[]> rows = query("SELECT password FROM users WHERE phone = ?", phone);
        return rows.isEmpty() ? null : rows.get(0)[0];
    }

    // Creates a new user account.
    public void addUser(String phone, String password) throws SQLException {
        exec("INSERT INTO users(phone, password) VALUES (?, ?)", phone, password);
    }

    // Returns the seats already booked for one show, e.g. ["A1", "C5"].
    // Used to grey out those seats so nobody can pick them again.
    public ArrayList<String> getBookedSeats(int showId) throws SQLException {
        ArrayList<String> seats = new ArrayList<>();
        for (String[] row : query("SELECT seat FROM bookings WHERE show_id = ?", showId))
            seats.add(row[0]); // each row has just one column: the seat name
        return seats;
    }

    // Saves a new show.
    // If that screen already has a show at the same date and time, the UNIQUE rule
    // makes the database refuse it, and an SQLException is thrown.
    public void addShow(String movie, String date, String time, String screen, double price) throws SQLException {
        exec("INSERT INTO shows(movie, show_date, show_time, screen, price) VALUES (?, ?, ?, ?, ?)",
                movie, date, time, screen, price);
    }

    // Deletes a show.
    // Its bookings are deleted first, otherwise they'd point to a show that no longer exists.
    public void deleteShow(int showId) throws SQLException {
        exec("DELETE FROM bookings WHERE show_id = ?", showId);
        exec("DELETE FROM shows WHERE id = ?", showId);
    }

    // Saves ONE booked seat. Booking 3 seats calls this 3 times.
    // If the seat is already taken, the UNIQUE rule makes it throw an SQLException.
    public void bookSeat(int showId, String seat, String name, String phone, double price) throws SQLException {
        exec("INSERT INTO bookings(show_id, seat, name, phone, price) VALUES (?, ?, ?, ?, ?)",
                showId, seat, name, phone, price);
    }

    // Deletes one booking, so that seat becomes free again.
    public void cancelBooking(int bookingId) throws SQLException {
        exec("DELETE FROM bookings WHERE id = ?", bookingId);
    }

    // Works out the price of one seat from its row letter (the first letter of the seat name).
    //   Rows A-B -> normal price
    //   Rows C-D -> 1.25 times
    //   Rows E-F -> 1.5 times
    // Example: normal price 200, seat "E3" -> 200 x 1.5 = 300
    // (chars can be compared like numbers: 'A' < 'B' < 'C' ...)
    public double seatPrice(double basePrice, String seat) {
        char row = seat.charAt(0);
        return row <= 'B' ? basePrice : row <= 'D' ? basePrice * 1.25 : basePrice * 1.5;
    }
}
