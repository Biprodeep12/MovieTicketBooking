import javax.swing.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

// ==========================================
// CONTROLLER: Coordinates between Model and View
// ==========================================
/*
 * The Controller connects the View (window) and the Model (database).
 * When the user clicks something, the Controller:
 *   1. reads what the user typed or selected (from the View)
 *   2. checks it is valid
 *   3. asks the Model to save or load data
 *   4. updates the View, or shows an error message
 *
 * The main flows (all of them use the same style of popup):
 *   Login / Logout: "Login" -> loginOrLogout() -> popup -> checkLogin() -> "Hi, <phone>"
 *   Book tickets:   click a show card -> (login first if needed) -> openShow() -> click seats
 *                   -> updateTotal() -> Continue -> bookTickets() -> popup asks a name -> "Booking Confirmed"
 *   Add a show:     "+ Add Show" -> addShow() -> popup -> saveShow() -> "Show Added"          (admin)
 *   Delete a show:  select a row -> "Delete Selected" -> deleteShow() -> confirm popup         (admin)
 *   Cancel booking: select a row -> "Cancel Selected" -> cancelBooking() -> confirm popup      (admin)
 *
 * In the form popups, checkLogin() / checkName() / saveShow() run when the main button is clicked.
 * They return an error message (shown in red, popup stays open) or null (all OK, popup closes).
 *
 * Login rules (kept very simple):
 *   - login is phone number + password (names aren't unique, so the name is asked while booking)
 *   - the admin is hardcoded below: phone "1234567890", password "movie"
 *   - a phone number that isn't saved yet -> a new account is created
 *   - a phone number that is saved        -> the password must match, else "Wrong Credentials"
 */
public class BookingController {
    private final BookingModel model;
    private final BookingView view;

    // The show the user is booking seats for (set when a show card is clicked).
    // It holds: {id, movie, date, time, screen, price, seatsBooked}, so show[0] = id and show[5] = price.
    private String[] show;

    // The admin account (hardcoded, not saved in the database)
    private static final String ADMIN_PHONE = "1234567890", ADMIN_PASSWORD = "movie";

    // The phone number of who is logged in, or null when nobody is logged in
    private String userPhone;

    // True if the admin is logged in
    private boolean isAdmin() {
        return ADMIN_PHONE.equals(userPhone);
    }

    // Saves the Model and View, then tells each button which method to run when clicked.
    // "e -> updateTotal()" is a lambda: a short way to write "when clicked, run updateTotal()".
    public BookingController(BookingModel model, BookingView view) {
        this.model = model;
        this.view = view;
        for (JToggleButton seat : view.seats)
            seat.addActionListener(e -> updateTotal()); // any seat clicked -> recalculate the total
        view.loginBtn.addActionListener(e -> loginOrLogout());
        view.backBtn.addActionListener(e -> view.showPage("shows"));
        view.bookBtn.addActionListener(e -> bookTickets());
        view.addShowBtn.addActionListener(e -> addShow());
        view.deleteShowBtn.addActionListener(e -> deleteShow());
        view.cancelBtn.addActionListener(e -> cancelBooking());
    }

    // Called once by main() to start the app:
    // create the tables -> load the data into the window -> show the window.
    public void startApp() {
        try {
            model.createTables();
        } catch (SQLException e) {
            showError(e);
        }
        refresh();
        view.setVisible(true); // the window only appears on screen after this line
    }

    // Loads everything again from the database and puts it on screen:
    // the show cards, the Shows table and the Bookings table.
    // Called after every change (book / add / delete) so the screen always shows the latest data.
    private void refresh() {
        try {
            ArrayList<String[]> shows = model.getShows();
            ArrayList<String[]> bookings = model.getBookings();
            view.fillTable(view.showData, shows);
            view.fillTable(view.bookingData, bookings);
            view.showsTitle.setText("All Shows (" + shows.size() + ")");
            view.bookingsTitle.setText("All Bookings (" + bookings.size() + ")");
            if (userPhone != null) { // the "Your Bookings" section under the cards
                ArrayList<String[]> mine = model.getUserBookings(userPhone);
                view.fillTable(view.myBookingData, mine);
                view.myBookingsTitle.setText("Your Bookings (" + mine.size() + ")");
            }
            // this::openShow means "when a card is clicked, call openShow() with that card's show"
            view.fillCards(shows, this::openShow);
            view.showPage("shows");
        } catch (SQLException e) {
            showError(e);
        }
    }

    // Shows a database error in a popup
    private void showError(SQLException e) {
        view.showMessage("Something Went Wrong", "Database error", e.getMessage());
    }

    // Runs when the top-right button is clicked.
    // Logged in -> log out. Logged out -> open the login popup (checkLogin() checks it).
    private void loginOrLogout() {
        if (userPhone != null)
            userPhone = null; // log out
        else
            view.askLogin(this::checkLogin); // if the login works, checkLogin() sets "userPhone"
        // update the header ("Hi, Admin" or "Hi, <phone>"), the admin tabs and "Your Bookings"
        view.setUser(userPhone == null ? null : isAdmin() ? "Admin" : userPhone, isAdmin());
        refresh();
    }

    // Called by the login popup with {phone, password}.
    // Returns an error message to show in the popup, or null if the login worked.
    private String checkLogin(String[] typed) {
        String phone = typed[0], password = typed[1];
        if (!phone.matches("[0-9]{10}")) // [0-9]{10} = exactly 10 digits
            return "Phone number must be 10 digits.";
        if (password.isEmpty())
            return "Please enter your password.";
        // the admin
        if (phone.equals(ADMIN_PHONE)) {
            if (!password.equals(ADMIN_PASSWORD))
                return "Wrong Credentials";
            userPhone = phone;
            return null;
        }
        // a normal user
        try {
            String saved = model.getPassword(phone); // null if this phone number is new
            if (saved == null)
                model.addUser(phone, password); // new phone number -> create the account
            else if (!saved.equals(password))
                return "Wrong Credentials";
            userPhone = phone;
            return null;
        } catch (SQLException e) {
            return "Database error: " + e.getMessage();
        }
    }

    // Runs when a show card is clicked. Opens the seat page for that show.
    // You must be logged in first, so if nobody is, the login popup opens.
    // All seats are unselected, and seats that are already booked are disabled (greyed out).
    private void openShow(String[] s) {
        if (userPhone == null) {
            loginOrLogout();
            if (userPhone == null) // the login popup was cancelled
                return;
        }
        show = s; // remember which show this is, for updateTotal() and bookTickets()
        view.showLabel.setText(s[1] + "   |   " + s[2] + "  " + s[3] + "   |   " + s[4]);
        try {
            ArrayList<String> booked = model.getBookedSeats(Integer.parseInt(s[0]));
            for (JToggleButton seat : view.seats) {
                seat.setSelected(false);
                seat.setEnabled(!booked.contains(seat.getText())); // booked -> disabled
            }
            updateTotal();
            view.showPage("seats");
        } catch (SQLException e) {
            showError(e);
        }
    }

    // Returns the names of the seats the user has clicked, e.g. ["A1", "A2"].
    private ArrayList<String> getSelectedSeats() {
        ArrayList<String> list = new ArrayList<>();
        for (JToggleButton seat : view.seats)
            if (seat.isSelected())
                list.add(seat.getText()); // the button's text is the seat name
        return list;
    }

    // Adds up the price of every selected seat and shows it, e.g. "Total: Rs. 450.00".
    private void updateTotal() {
        double total = 0;
        for (String seat : getSelectedSeats())
            total += model.seatPrice(Double.parseDouble(show[5]), seat); // show[5] = normal price
        view.totalLabel.setText(String.format("Total: Rs. %.2f", total)); // %.2f = 2 decimal places
    }

    // Runs when "Continue" is clicked on the seat page.
    //   1. At least one seat must be selected
    //   2. A popup shows the booking summary and asks for a name; stop if the user clicks Cancel
    //   3. Each seat is saved as its own booking (with that name and the logged-in phone number)
    //   4. Show a confirmation and refresh the screen
    private void bookTickets() {
        ArrayList<String> seats = getSelectedSeats();
        if (seats.isEmpty()) {
            view.showMessage("No Seats Selected", "Please select at least one seat.");
            return; // "return" stops the method here
        }
        String seatList = String.join(", ", seats), total = view.totalLabel.getText();
        String when = BookingView.niceDate(show[2]) + "  \u2022  " + BookingView.niceTime(show[3]) + "  \u2022  "
                + show[4];
        String[] typed = view.askName(show[1], total, when, "Seats: " + seatList, this::checkName);
        if (typed == null) // the popup was cancelled
            return;
        String name = typed[0];
        try {
            for (String seat : seats) // one database row per seat
                model.bookSeat(Integer.parseInt(show[0]), seat, name, userPhone,
                        model.seatPrice(Double.parseDouble(show[5]), seat));
            view.showMessage("Booking Confirmed", show[1], when, "Seats: " + seatList, "Name: " + name,
                    "<b>" + total + "</b>");
        } catch (SQLException e) {
            showError(e);
        }
        refresh();
    }

    // Called by the booking popup with {name}. Returns an error message, or null if it's OK.
    private String checkName(String[] typed) {
        return typed[0].isEmpty() ? "Please enter a name for the booking." : null;
    }

    // Runs when "+ Add Show" is clicked: opens the popup, saveShow() checks and saves it.
    private void addShow() {
        String[] s = view.askShow(this::saveShow); // s = {movie, screen, date, time, price}
        if (s == null) // cancelled
            return;
        refresh();
        view.showMessage("Show Added", s[0], BookingView.niceDate(s[2]) + "  \u2022  "
                + BookingView.niceTime(s[3]) + "  \u2022  " + s[1], "It is now open for booking.");
    }

    // Called by the "Add a New Show" popup with {movie, screen, date, time, price}.
    // Checks each value, then saves the show.
    // Returns an error message to show in the popup, or null if it was saved.
    // .matches(...) checks text against a pattern (a "regular expression"):
    //   [0-9]{4} = exactly 4 digits,  [0-9]+ = one or more digits
    // The pattern only checks the shape, so "2026-13-45" or "25:99" would pass it.
    // LocalDate.parse / LocalTime.parse check that the date/time really exists;
    // if not, they throw an exception, which we catch and turn into a message.
    private String saveShow(String[] s) {
        String movie = s[0], screen = s[1], date = s[2], time = s[3], price = s[4];
        if (movie.isEmpty() || screen.isEmpty())
            return "Please enter the movie name and screen.";
        if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) // like 2026-11-05
            return "Date must be like 2026-11-05.";
        try {
            if (LocalDate.parse(date).isBefore(LocalDate.now()))
                return "Date can't be in the past.";
        } catch (DateTimeParseException e) {
            return "That date doesn't exist. Check the month and day.";
        }
        if (!time.matches("[0-9]{2}:[0-9]{2}")) // like 18:00
            return "Time must be like 18:00.";
        try {
            LocalTime.parse(time);
        } catch (DateTimeParseException e) {
            return "That time doesn't exist. Use 00:00 to 23:59.";
        }
        if (!price.matches("[0-9]+(\\.[0-9]+)?") || Double.parseDouble(price) <= 0) // like 150 or 149.50
            return "Price must be a number more than 0.";
        try {
            model.addShow(movie, date, time, screen, Double.parseDouble(price));
            return null;
        } catch (SQLException e) {
            // "UNIQUE" in the error means that screen already has a show at that time
            return e.getMessage().contains("UNIQUE") ? "This screen already has a show at that date and time."
                    : "Could not add show: " + e.getMessage();
        }
    }

    // Returns the values in the selected row of a table, as text.
    // Safe to call: the Delete / Cancel buttons are only clickable when a row is selected.
    private String[] selectedRow(JTable table) {
        int row = table.getSelectedRow();
        String[] values = new String[table.getColumnCount()];
        for (int c = 0; c < values.length; c++)
            values[c] = table.getValueAt(row, c).toString();
        return values;
    }

    // Runs when "Delete Selected" is clicked on the Shows tab.
    // Shows what will be deleted, and deletes the show (and its bookings) if the user confirms.
    private void deleteShow() {
        String[] r = selectedRow(view.showTable); // {id, movie, date, time, screen, price, seatsBooked}
        int booked = Integer.parseInt(r[6]);
        String warning = booked == 0 ? null : booked + " booked seat(s) will also be cancelled.";
        if (!view.askConfirm("Delete Show", "Delete Show", warning, r[1],
                BookingView.niceDate(r[2]) + "  \u2022  " + BookingView.niceTime(r[3]) + "  \u2022  " + r[4],
                "Base price: Rs. " + r[5]))
            return; // user clicked "No, keep it"
        try {
            model.deleteShow(Integer.parseInt(r[0]));
            refresh();
        } catch (SQLException e) {
            showError(e);
        }
    }

    // Runs when "Cancel Selected" is clicked on the Bookings tab.
    // Shows the booking details, and deletes it if the user confirms (the seat becomes free again).
    private void cancelBooking() {
        String[] r = selectedRow(view.bookingTable); // {id, movie, date, time, seat, name, phone, price}
        if (!view.askConfirm("Cancel Booking", "Cancel Booking", "Seat " + r[4] + " will become available again.",
                r[1], BookingView.niceDate(r[2]) + "  \u2022  " + BookingView.niceTime(r[3]),
                "Seat: " + r[4] + "   |   Price: Rs. " + r[7], "Name: " + r[5] + "   |   Phone: " + r[6]))
            return;
        try {
            model.cancelBooking(Integer.parseInt(r[0]));
            refresh();
        } catch (SQLException e) {
            showError(e);
        }
    }
}
