# Movie Ticket Booking System

WCA Mini Project: Class Group A1, Team 1.
A simple Java Swing app using **MVC** and **JDBC (SQLite)**, one class per file:

- `MovieBookingApp.java`: `main`, starts the app
- **Model** (`BookingModel.java`): database tables, SQL, seat pricing
- **View** (`BookingView.java`): Swing GUI window with 3 tabs
- **Controller** (`BookingController.java`): button actions, input checks, connects Model and View

## Features

- **Light, minimal UI**: available seats are light grey, selected seats turn red, booked seats turn white.
- **Simple login**: Login button (top right) asks for phone and password. A new phone number creates an account; a saved one must match its password, else "Wrong Credentials".
- **Admin**: hardcoded as phone `1234567890`, password `movie`. Only the admin sees the Manage Shows and Bookings tabs.
- **Booking flow**: pick a show card (log in first if needed) → select seats → popup asks the name for the booking → booked. Your own bookings show under the cards.
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
- **`users`**: phone (PRIMARY KEY), password.
- **JDBC**: connects to SQLite with `DriverManager.getConnection("jdbc:sqlite:movie_booking.db")`.
- **`PreparedStatement` with `?`**: safe from SQL injection.
- **try-with-resources**: closes the connection automatically.

### Model: `BookingModel`

| Method | What it does |
|---|---|
| `exec(sql, args...)` | Runs INSERT / DELETE / CREATE |
| `query(sql, args...)` | Runs SELECT, returns rows as `ArrayList<String[]>` |
| `createTables()` | Creates the tables and adds 3 sample shows if empty |
| `getShows()` / `getBookings()` | Reads all shows (with seats booked count) / bookings (bookings JOIN shows) |
| `getBookedSeats(showId)` | Seats already booked for a show |
| `getUserBookings(phone)` | One user's bookings (for "Your Bookings") |
| `getPassword(phone)` / `addUser(phone, password)` | Reads the saved password / creates a user account |
| `addShow(...)` / `deleteShow(id)` | Adds a show / deletes a show and its bookings |
| `bookSeat(...)` / `cancelBooking(id)` | Saves / deletes one booking |
| `seatPrice(base, seat)` | Rows A–B ×1, C–D ×1.25, E–F ×1.5 |

### View: `BookingView` (extends `JFrame`)

- **Header**: logo + title on the left, "Hi, name" + Login/Logout on the right. The logo is drawn with shapes (`Logo` class) so it stays sharp.
- **Tabs** (`JTabbedPane`): Shows for everyone; Manage Shows and Bookings for the admin only.
- **Shows** has 2 pages switched by `CardLayout`: `"shows"` (cards) and `"seats"` (seat grid).
- **Seat grid**: 60 `JToggleButton`s in a 6×10 `GridLayout`.
  - Selected = red.
  - Disabled = booked (white).
- **Tables**: `JTable` + `DefaultTableModel`, read-only.

| Method | What it does |
|---|---|
| `setTheme()` | Sets default fonts/colours with `UIManager` (called before the window is built) |
| `fillCards(shows, onClick)` | Makes one `ShowCard` per show (poster colour, date/time, seats-left bar, price, Book / Sold out) |
| `showPage(name)` | Switches between the cards page and the seats page |
| `popup(...)` | Builds every popup the same way: heading, grey summary box, body, red error line, buttons |
| `askForm(...)` | Form popup; calls the Controller's `onConfirm` on submit and stays open showing the error until it returns `null` |
| `askLogin(...)` / `askName(...)` / `askShow(...)` | Login / booking name / Add Show popups (built on `askForm`), return what was typed or `null` |
| `setUser(name, admin)` | Updates the header, shows "Your Bookings", adds/removes the admin tabs |
| `askConfirm(...)` | Styled "are you sure?" popup with a summary of what will be deleted |
| `fillTable(model, rows)` | Replaces a table's rows |
| `showMessage(title, heading, lines...)` | Styled message popup with an OK button |
| `button`, `chip`, `heading`, `table`, `row`, `layout`, `tableSection` | Small helpers that build styled components |

### Controller: `BookingController`

| Method | What it does |
|---|---|
| constructor | Connects every button to its method (`addActionListener`) |
| `startApp()` | Creates tables, loads data, shows the window |
| `refresh()` | Reloads cards and both tables from the database, goes back to the cards page |
| `openShow(show)` | Card clicked → disables booked seats → opens the seats page |
| `updateTotal()` | Adds up `seatPrice` of selected seats |
| `loginOrLogout()` / `checkLogin(...)` | Login popup → admin check or create/check account / logs out |
| `bookTickets()` / `checkName(...)` | Checks seats → popup asks the name → saves each seat with that name + logged-in phone → confirmation |
| `addShow()` / `saveShow(...)` | Popup → checks date, time, price → saves the show → confirmation |
| `deleteShow()` / `cancelBooking()` | Confirm popup with the row's details → deletes the show (and its bookings) / booking |

### Flows

- **Book tickets**:
  1. Click a show card → `openShow()`.
  2. Click seats → `updateTotal()`.
  3. Continue → `bookTickets()` → name popup → `model.bookSeat()` → "Booking Confirmed" → `refresh()`.
- **Login**: Login → `checkLogin()` → `view.setUser()` → `refresh()` (loads "Your Bookings").
- **Add show**: "+ Add Show" → popup → `saveShow()` → validation → `model.addShow()` → "Show Added" → `refresh()`.
  - The database rejects a clash on the same screen, date and time; the error shows in red inside the popup.
- **Delete / Cancel**: select a row (the button becomes active) → confirm popup with details → deleted → `refresh()`.
- **Errors**: form errors show in red inside the popup (it stays open); database errors show in a message popup.

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
