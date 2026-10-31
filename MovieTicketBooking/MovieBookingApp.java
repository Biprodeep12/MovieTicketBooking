/*
 * Movie Ticket Booking System - the program starts here.
 *
 * The code is split into 3 parts (this is called MVC):
 *   BookingModel.java      -> Model:      saves and reads data from the database
 *   BookingView.java       -> View:       the window you see (buttons, tables, seats)
 *   BookingController.java -> Controller: decides what happens when you click something
 *
 * What happens when you run the program:
 *   1. setTheme()      sets the fonts and colours
 *   2. the Model, View and Controller objects are created
 *   3. startApp()      creates the database tables, loads the data and opens the window
 */
public class MovieBookingApp {
    public static void main(String[] args) {
        // Step 1: must happen before any window or button is created
        BookingView.setTheme();

        // Step 2 and 3: create the 3 parts, give the Model and View to the Controller, then start
        new BookingController(new BookingModel(), new BookingView()).startApp();
    }
}
