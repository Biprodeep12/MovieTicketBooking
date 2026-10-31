import javax.swing.*;
import java.sql.SQLException;
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
 * The 3 main flows:
 *   Book tickets:  click a show card -> openShow() -> click seats -> updateTotal()
 *                  -> click Continue -> bookTickets() -> popup -> seats saved -> refresh()
 *   Add a show:    fill the form -> click "+ Add Show" -> addShow() -> saved -> refresh()
 *   Delete/Cancel: select a row -> click the button -> deleteSelected() -> Yes -> deleted -> refresh()
 */
public class BookingController {
    private final BookingModel model;
    private final BookingView view;

    // The show the user is booking seats for (set when a show card is clicked).
    // It holds: {id, movie, date, time, screen, price}, so show[0] = id and show[5] = price.
    private String[] show;

    // Saves the Model and View, then tells each button which method to run when clicked.
    // "e -> updateTotal()" is a lambda: a short way to write "when clicked, run updateTotal()".
    public BookingController(BookingModel model, BookingView view) {
        this.model = model;
        this.view = view;
        for (JToggleButton seat : view.seats)
            seat.addActionListener(e -> updateTotal()); // any seat clicked -> recalculate the total
        view.backBtn.addActionListener(e -> view.showPage("shows"));
        view.bookBtn.addActionListener(e -> bookTickets());
        view.addShowBtn.addActionListener(e -> addShow());
        // Both delete buttons use the same method; we pass in which table to delete from
        view.deleteShowBtn
                .addActionListener(e -> deleteSelected(view.showTable, "Delete this show and all its bookings?"));
        view.cancelBtn.addActionListener(e -> deleteSelected(view.bookingTable, "Cancel this booking?"));
    }

    // Called once by main() to start the app:
    // create the tables -> load the data into the window -> show the window.
    public void startApp() {
        try {
            model.createTables();
            refresh();
        } catch (SQLException e) {
            view.showMessage("Database error: " + e.getMessage());
        }
        view.setVisible(true); // the window only appears on screen after this line
    }

    // Loads everything again from the database and puts it on screen:
    // the show cards, the Shows table and the Bookings table.
    // Called after every change (book / add / delete) so the screen always shows the latest data.
    private void refresh() throws SQLException {
        ArrayList<String[]> shows = model.getShows();
        ArrayList<String[]> bookings = model.getBookings();
        view.fillTable(view.showData, shows);
        view.fillTable(view.bookingData, bookings);
        view.showsTitle.setText("All Shows (" + shows.size() + ")");
        view.bookingsTitle.setText("All Bookings (" + bookings.size() + ")");
        // this::openShow means "when a card is clicked, call openShow() with that card's show"
        view.fillCards(shows, this::openShow);
        view.showPage("shows");
    }

    // Runs when a show card is clicked. Opens the seat page for that show.
    // All seats are unselected, and seats that are already booked are disabled (greyed out).
    private void openShow(String[] s) {
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
            view.showMessage("Database error: " + e.getMessage());
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
    //   2. A popup asks for name and phone (stop here if the user clicks Cancel)
    //   3. The name must not be empty and the phone must be 10 digits
    //   4. Each seat is saved as its own booking, then the screen is refreshed
    private void bookTickets() {
        ArrayList<String> seats = getSelectedSeats();
        if (seats.isEmpty()) {
            view.showMessage("Please select at least one seat.");
            return; // "return" stops the method here
        }
        String[] customer = view.askCustomer(view.showLabel.getText(), String.join(", ", seats),
                view.totalLabel.getText());
        if (customer == null) // null means the popup was cancelled
            return;
        String name = customer[0], phone = customer[1];
        if (name.isEmpty()) {
            view.showMessage("Please enter your name.");
            return;
        }
        if (!phone.matches("[0-9]{10}")) { // [0-9]{10} = exactly 10 digits
            view.showMessage("Phone number must be 10 digits.");
            return;
        }
        try {
            for (String seat : seats) // one database row per seat
                model.bookSeat(Integer.parseInt(show[0]), seat, name, phone,
                        model.seatPrice(Double.parseDouble(show[5]), seat));
            view.showMessage("Booked seats " + seats + " for " + name + ".\n" + view.totalLabel.getText());
            refresh();
        } catch (SQLException e) {
            view.showMessage("Could not book: " + e.getMessage());
        }
    }

    // Runs when "+ Add Show" is clicked.
    // Reads the 5 form fields, checks each one, and saves the show if everything is OK.
    // .matches(...) checks text against a pattern (a "regular expression"):
    //   [0-9]{4} = exactly 4 digits,  [0-9]+ = one or more digits
    private void addShow() {
        String movie = view.movieField.getText().trim(), date = view.dateField.getText().trim();
        String time = view.timeField.getText().trim(), screen = view.screenField.getText().trim();
        String price = view.priceField.getText().trim(); // trim() removes spaces at the start/end
        // Check the fields one by one; stop at the first problem
        String error = null;
        if (movie.isEmpty() || screen.isEmpty())
            error = "Please enter the movie name and screen.";
        else if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) // like 2026-11-05
            error = "Date must be like 2026-11-05.";
        else if (!time.matches("[0-9]{2}:[0-9]{2}")) // like 18:00
            error = "Time must be like 18:00.";
        else if (!price.matches("[0-9]+(\\.[0-9]+)?") || Double.parseDouble(price) <= 0) // like 150 or 149.50
            error = "Price must be a number more than 0.";
        if (error != null) {
            view.showMessage(error);
            return;
        }
        try {
            model.addShow(movie, date, time, screen, Double.parseDouble(price));
            view.movieField.setText(""); // clear the movie name so the next show can be typed
            refresh();
        } catch (SQLException e) {
            // "UNIQUE" in the error means that screen already has a show at that time
            view.showMessage(e.getMessage().contains("UNIQUE") ? "This screen already has a show at that date and time."
                    : "Could not add show: " + e.getMessage());
        }
    }

    // Used by 2 buttons:
    //   "Delete Selected" on the Shows tab    -> deletes the show and its bookings
    //   "Cancel Selected" on the Bookings tab -> deletes one booking
    // It asks "Are you sure?" first, then gets the ID from the first column of the selected row.
    private void deleteSelected(JTable table, String question) {
        int row = table.getSelectedRow(); // safe: the button is only clickable when a row is selected
        if (!view.askYesNo(question)) // user clicked No
            return;
        try {
            int id = Integer.parseInt(table.getValueAt(row, 0).toString()); // column 0 = ID
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
