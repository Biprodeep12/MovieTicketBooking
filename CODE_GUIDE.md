# Code Guide

## Files
```
├── .gitignore
├── CODE_GUIDE.md                  ← this file
└── MovieTicketBooking/
    ├── MovieBookingApp.java       ← all the code (Model + View + Controller)
    ├── README.md                  ← how to compile and run
    ├── sqlite-jdbc-3.53.2.1.jar   ← SQLite JDBC driver
    └── movie_booking.db           ← database (created on first run, not committed)
```
The code follows the same layout as the instructor's sample MVC program: one file, with Model, View and Controller classes marked by `Start of ...` / `End of ...` comments.

## Database tables
| Table | Columns | Rule |
|---|---|---|
| `shows` | id, movie, show_date, show_time, screen, price | `UNIQUE(screen, show_date, show_time)`: one show per screen slot |
| `bookings` | id, show_id, seat, name, phone, price | `UNIQUE(show_id, seat)`: a seat can't be booked twice |

One booking row = one seat. `bookings.show_id` links to `shows.id`.

## 1. `BookingModel` (Model)
Only SQL and pricing. No Swing code.

| Method | What it does |
|---|---|
| `connect()` | Opens a connection to `movie_booking.db` |
| `createTables()` | Creates both tables and adds 3 sample shows if the table is empty |
| `getShows()` | All shows as a list of `String[]` |
| `getBookings()` | All bookings joined with their show details |
| `getBookedSeats(showId)` | Seats already booked for a show |
| `addShow(...)` | Inserts a show |
| `deleteShow(id)` | Deletes the show's bookings, then the show |
| `bookSeat(...)` | Inserts one booked seat |
| `cancelBooking(id)` | Deletes one booking |
| `seatPrice(base, seat)` | Pricing: rows A–B = base, C–D = base × 1.25, E–F = base × 1.5 |

`PreparedStatement` with `?` is used for user input. `try (...)` closes the connection automatically.

## 2. `BookingView` (View)
Only Swing components and layout. No SQL.
- **Book Tickets tab**: `showBox` dropdown, 60 seat buttons (`JToggleButton`) in a 6 × 10 `GridLayout`, name and phone fields, total label, Book button.
- **Shows tab**: fields to add a show, a `JTable` of shows, Delete button.
- **Bookings tab**: a `JTable` of bookings, Cancel button.
- Helpers: `fillTable()` fills a table, `showMessage()` and `askYesNo()` show popups.

A seat button that is pressed in = selected. A greyed-out (disabled) seat = already booked.

## 3. `BookingController` (Controller)
Connects buttons to Model methods.

| Method | What it does |
|---|---|
| constructor | Adds listeners to the dropdown, seats and buttons |
| `startApp()` | Creates tables, loads data, shows the window |
| `refresh()` | Reloads tables, dropdown and seats from the database |
| `loadSeats()` | Clears selections and greys out booked seats |
| `updateTotal()` | Adds up the price of the selected seats |
| `bookTickets()` | Checks seats, name and 10-digit phone, then saves each seat |
| `addShow()` | Checks movie, date (`yyyy-mm-dd`), time (`HH:mm`) and price, then saves |
| `deleteShow()` / `cancelBooking()` | Delete the row selected in the table, after a Yes/No popup |

## How a booking works
1. Pick a show → `loadSeats()` → booked seats are greyed out.
2. Click seats → `updateTotal()` → total price is shown.
3. Enter name and phone, click **Book** → `bookTickets()` → `model.bookSeat()` → `refresh()`.

## Required features
| Requirement | Where |
|---|---|
| Visual seat selection matrix | `seats[]` grid in `BookingView` |
| Showtime scheduling | `addShow()`, `deleteShow()`, UNIQUE screen slot |
| Ticket pricing | `seatPrice()`, `updateTotal()` |
| Customer booking database | `bookings` table, `bookSeat()`, `cancelBooking()` |
| MVC + GUI + JDBC | `BookingModel` / `BookingView` / `BookingController`, Swing, `DriverManager` |
