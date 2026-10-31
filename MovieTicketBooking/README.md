# Movie Ticket Booking System

WCA Mini Project: Class Group A1, Team 1.
A simple Java Swing app using **MVC** and **JDBC (SQLite)**, one class per file:

- `MovieBookingApp.java`: `main`, starts the app
- **Model** (`BookingModel.java`): database tables, SQL, seat pricing
- **View** (`BookingView.java`): Swing GUI window with 3 tabs
- **Controller** (`BookingController.java`): button actions, input checks, connects Model and View

## Features

- **Light, minimal UI**: available seats are light grey, selected seats turn red, booked seats turn white.
- **Booking flow**: pick a show card → select seats → enter name/phone in a popup → booked.
- **Seat selection matrix**: rows A–F × seats 1–10. Booked seats are disabled.
- **Showtime scheduling**: add or delete shows. A screen can't have two shows at the same date and time.
- **Ticket pricing**: rows A–B use the normal price, C–D ×1.25, E–F ×1.5.
- **Booking database**: name, phone, seat and price are saved. A booking can be cancelled. A seat can't be booked twice.

## Code Guide

### Architecture (MVC)

- **Model**: talks to the database only. No Swing code.
- **View**: builds the window only. No SQL.
- **Controller**: reacts to clicks, checks input, calls the Model, then updates the View.
- `main` → `setTheme()` → creates Model, View, Controller → `startApp()`.

### Database

- **`shows`**: id, movie, show_date, show_time, screen, price.
  - `UNIQUE(screen, show_date, show_time)` stops two shows on the same screen at the same time.
- **`bookings`**: id, show_id, seat, name, phone, price.
  - One row per seat.
  - `UNIQUE(show_id, seat)` stops a seat being booked twice.
- **JDBC**: connects to SQLite with `DriverManager.getConnection("jdbc:sqlite:movie_booking.db")`.
- **`PreparedStatement` with `?`**: safe from SQL injection.
- **try-with-resources**: closes the connection automatically.

### Model: `BookingModel`

| Method | What it does |
|---|---|
| `exec(sql, args...)` | Runs INSERT / DELETE / CREATE |
| `query(sql, args...)` | Runs SELECT, returns rows as `ArrayList<String[]>` |
| `createTables()` | Creates the tables and adds 3 sample shows if empty |
| `getShows()` / `getBookings()` | Reads all shows / bookings (bookings JOIN shows) |
| `getBookedSeats(showId)` | Seats already booked for a show |
| `addShow(...)` / `deleteShow(id)` | Adds a show / deletes a show and its bookings |
| `bookSeat(...)` / `cancelBooking(id)` | Saves / deletes one booking |
| `seatPrice(base, seat)` | Rows A–B ×1, C–D ×1.25, E–F ×1.5 |

### View: `BookingView` (extends `JFrame`)

- **3 tabs** (`JTabbedPane`): Book Tickets, Shows, Bookings.
- **Book Tickets** has 2 pages switched by `CardLayout`: `"shows"` (cards) and `"seats"` (seat grid).
- **Seat grid**: 60 `JToggleButton`s in a 6×10 `GridLayout`.
  - Selected = red.
  - Disabled = booked (white).
- **Tables**: `JTable` + `DefaultTableModel`, read-only.

| Method | What it does |
|---|---|
| `setTheme()` | Sets default fonts/colours with `UIManager` (called before the window is built) |
| `fillCards(shows, onClick)` | Makes one clickable card per show |
| `showPage(name)` | Switches between the cards page and the seats page |
| `askCustomer(...)` | Name/phone popup (`JDialog`), returns `{name, phone}` or `null` if cancelled |
| `fillTable(model, rows)` | Replaces a table's rows |
| `showMessage()` / `askYesNo()` | Simple `JOptionPane` dialogs |
| `button`, `chip`, `heading`, `table`, `row`, `layout`, `tableSection` | Small helpers that build styled components |

### Controller: `BookingController`

| Method | What it does |
|---|---|
| constructor | Connects every button to its method (`addActionListener`) |
| `startApp()` | Creates tables, loads data, shows the window |
| `refresh()` | Reloads cards and both tables from the database, goes back to the cards page |
| `openShow(show)` | Card clicked → disables booked seats → opens the seats page |
| `updateTotal()` | Adds up `seatPrice` of selected seats |
| `bookTickets()` | Checks seats → popup → checks name/phone → saves each seat |
| `addShow()` | Checks the form (date, time, price) → saves the show |
| `deleteSelected(table, question)` | Confirms → deletes the selected show or booking |

### Flows

- **Book tickets**:
  1. Click a show card → `openShow()`.
  2. Click seats → `updateTotal()`.
  3. Continue → `bookTickets()` → popup → `model.bookSeat()` for each seat → `refresh()`.
- **Add show**: fill the form → `addShow()` → validation → `model.addShow()` → `refresh()`.
  - The database rejects a clash on the same screen, date and time, and the user sees a message.
- **Delete / Cancel**: select a row (the button becomes active) → confirm → `deleteSelected()` → `refresh()`.
- **Errors**: every database call is in `try/catch (SQLException)`, and the error is shown in a message box.

## Requirements

- JDK 8 or newer
- `sqlite-jdbc-3.53.2.1.jar` in this folder

## Compile and Run

**Windows**

```
javac -cp ".;sqlite-jdbc-3.53.2.1.jar" *.java
java -cp ".;sqlite-jdbc-3.53.2.1.jar" MovieBookingApp
```

**Linux / macOS**

```
javac -cp ".:sqlite-jdbc-3.53.2.1.jar" *.java
java -cp ".:sqlite-jdbc-3.53.2.1.jar" MovieBookingApp
```

`movie_booking.db` is created on first run with 3 sample shows. Delete it to reset.
