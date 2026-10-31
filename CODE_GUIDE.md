# Code Guide

This guide explains how the Movie Ticket Booking app works.
You only need basic Java (classes, methods, `if`, loops, arrays) to follow it.

## Files

```
├── CODE_GUIDE.md                  ← this file
└── MovieTicketBooking/
    ├── MovieBookingApp.java       ← the program starts here (main method)
    ├── BookingModel.java          ← Model: the database code
    ├── BookingView.java           ← View: the window (GUI)
    ├── BookingController.java     ← Controller: what happens when you click
    ├── README.md                  ← how to compile and run
    ├── sqlite-jdbc-3.53.2.1.jar   ← lets Java talk to SQLite
    └── movie_booking.db           ← the database file (created on first run)
```

## The big idea: MVC

The code is split into 3 parts. Each part has one job:

| Part | File | Its job | Example |
|---|---|---|---|
| **Model** | `BookingModel.java` | Saves and reads data | "Save seat A1 for Rahul" |
| **View** | `BookingView.java` | Draws the window | Buttons, seat grid, tables |
| **Controller** | `BookingController.java` | Connects the two | "Book clicked → check input → tell Model to save → update View" |

Why split it? Each file stays small, and you always know where to look:
- The Model has **no buttons**.
- The View has **no SQL**.
- The Controller is the only part that uses both.

## What happens when the app starts

1. `main()` in `MovieBookingApp.java` runs.
2. `BookingView.setTheme()` sets the fonts and colours.
3. The Model, View and Controller objects are created.
4. The Controller's constructor tells each button which method to run when it's clicked.
5. `startApp()`:
   - creates the database tables (and 3 sample shows the first time),
   - loads the data into the window,
   - shows the window.

## The database

The data is stored in `movie_booking.db`, a **SQLite** database (a single file, no server needed).
Java talks to it through **JDBC**.

**Table `shows`**: one row per show.

| id | movie | show_date | show_time | screen | price |
|---|---|---|---|---|---|
| 1 | Inception | 2026-11-05 | 10:00 | Screen 1 | 180 |

**Table `bookings`**: one row per booked **seat**. Booking 3 seats = 3 rows.

| id | show_id | seat | name | phone | price |
|---|---|---|---|---|---|
| 1 | 1 | A1 | Rahul | 9876543210 | 180 |

- `show_id` says which show the seat belongs to (it matches `shows.id`).

**Rules the database checks for us (`UNIQUE`)**:
- A screen can't have 2 shows at the same date and time.
- The same seat can't be booked twice for one show.

If a rule is broken, the database refuses the save and Java gets an `SQLException`.
The Controller catches it and shows a message.

### Database terms used in the code

| Term | Meaning |
|---|---|
| `Connection` | An open link to the database file |
| `PreparedStatement` | An SQL command with `?` blanks that get filled in. Safer than joining strings (stops SQL injection) |
| `ResultSet` | The rows a SELECT gives back, read one at a time with `rs.next()` |
| `try (...) { }` | "try-with-resources": closes the connection automatically when done |
| `JOIN` | Combines two tables. Used to show the movie name next to each booking |

## 1. Model: `BookingModel.java`

| Method | What it does |
|---|---|
| `connect()` | Opens the database file |
| `exec(sql, values...)` | Runs SQL that changes data (INSERT, DELETE, CREATE) |
| `query(sql, values...)` | Runs a SELECT and returns the rows as a list of `String[]` |
| `createTables()` | Creates both tables, and adds 3 sample shows if there are none |
| `getShows()` | All shows, earliest first |
| `getBookings()` | All bookings, newest first, with the movie name, date and time |
| `getBookedSeats(showId)` | Seats already taken for a show, e.g. `["A1", "C5"]` |
| `addShow(...)` | Saves a new show |
| `deleteShow(id)` | Deletes a show and all its bookings |
| `bookSeat(...)` | Saves one booked seat |
| `cancelBooking(id)` | Deletes one booking, so the seat is free again |
| `seatPrice(price, seat)` | The price of one seat (see below) |

**Seat pricing**: seats further back cost more.

| Rows | Price | Example (normal price Rs. 200) |
|---|---|---|
| A, B | normal | Rs. 200 |
| C, D | × 1.25 | Rs. 250 |
| E, F | × 1.5 | Rs. 300 |

## 2. View: `BookingView.java`

The window is built with **Swing**, Java's built-in GUI library.

**The 3 tabs**:
1. **Book Tickets** has 2 pages. Only one is visible at a time:
   - `"shows"`: one card per show. Click a card to pick it.
   - `"seats"`: the 6 × 10 seat grid, the total price and the **Continue** button.
2. **Shows**: a form to add a show, and a table of all shows with a **Delete Selected** button.
3. **Bookings**: a table of all bookings with a **Cancel Selected** button.

**Seat colours**:
- Light grey: available.
- Red: selected by you.
- White with grey text: already booked (the button is disabled).

### Swing parts used

| Swing class | What it is | Used for |
|---|---|---|
| `JFrame` | A window | The main window (`BookingView extends JFrame`) |
| `JTabbedPane` | Tabs | The 3 tabs |
| `CardLayout` | Shows one panel at a time, like a deck of cards | Switching between the "shows" and "seats" pages |
| `JToggleButton` | A button that stays pressed in | Seats (pressed = selected) |
| `JTable` + `DefaultTableModel` | A table + the data inside it | Shows and Bookings tables |
| `JDialog` | A popup window | Asking for name and phone |
| `JOptionPane` | Ready-made message / Yes-No boxes | Errors and "Are you sure?" |
| `GridLayout`, `BorderLayout`, `FlowLayout` | Ways to arrange components | Grid of seats, top/middle/bottom, a row |
| `UIManager` | Sets the default look of all components | `setTheme()` |

### Methods the Controller calls

| Method | What it does |
|---|---|
| `setTheme()` | Sets fonts and colours for the whole app (runs before the window is built) |
| `fillCards(shows, onClick)` | Makes one clickable card per show |
| `showPage(name)` | Switches to the `"shows"` or `"seats"` page |
| `askCustomer(show, seats, total)` | Popup asking name and phone. Returns `{name, phone}`, or `null` if cancelled |
| `fillTable(table, rows)` | Replaces all rows in a table |
| `showMessage(text)` | Message box with an OK button |
| `askYesNo(text)` | Yes / No box. Returns `true` for Yes |

The other private methods (`button`, `chip`, `heading`, `table`, `row`, `fill`, `layout`, `tableSection`, `border`, `cardBorder`) are small helpers.
They create styled components so the same styling code isn't repeated everywhere.

## 3. Controller: `BookingController.java`

| Method | When it runs | What it does |
|---|---|---|
| constructor | App start | Connects each button to its method |
| `startApp()` | App start | Creates tables, loads data, shows the window |
| `refresh()` | After every change | Reloads the cards and both tables from the database |
| `openShow(show)` | A show card is clicked | Disables booked seats, opens the seats page |
| `getSelectedSeats()` | Used by other methods | The seats you've clicked, e.g. `["A1", "A2"]` |
| `updateTotal()` | A seat is clicked | Adds up the seat prices and shows the total |
| `bookTickets()` | Continue is clicked | Checks the input, opens the popup, saves each seat |
| `addShow()` | "+ Add Show" is clicked | Checks the form, saves the show |
| `deleteSelected(table, question)` | Delete / Cancel is clicked | Asks "Are you sure?", then deletes the selected show or booking |

### Java terms used in the Controller

| Code | Meaning |
|---|---|
| `button.addActionListener(e -> bookTickets())` | "When this button is clicked, run `bookTickets()`". `e -> ...` is a **lambda**, a short way to write a small method |
| `this::openShow` | Passes the `openShow` method itself, so the View can call it when a card is clicked |
| `text.matches("[0-9]{10}")` | Checks the text against a pattern (**regular expression**). `[0-9]{10}` means exactly 10 digits |
| `try { } catch (SQLException e) { }` | If the database gives an error, show a message instead of crashing |

## Step by step: booking tickets

1. You click a show card.
   - `openShow()` asks the Model for the booked seats and disables them.
   - The seats page opens.
2. You click some seats.
   - Each click runs `updateTotal()`, which updates the total price.
3. You click **Continue**. `bookTickets()` then:
   - checks that at least one seat is selected,
   - opens the popup for your name and phone,
   - checks the name isn't empty and the phone has 10 digits,
   - calls `model.bookSeat()` once for each seat,
   - shows a "Booked" message.
4. `refresh()` reloads everything, and you're back on the show cards.

## Step by step: adding a show

1. Fill in the form on the **Shows** tab and click **+ Add Show**.
2. `addShow()` checks each field:
   - the movie and screen aren't empty,
   - the date looks like `2026-11-05`,
   - the time looks like `18:00`,
   - the price is a number above 0.
3. `model.addShow()` saves it.
   - If that screen already has a show at that time, the database refuses it.
   - You see "This screen already has a show at that date and time."
4. `refresh()` shows the new show in the table and as a card.

## Step by step: deleting a show or cancelling a booking

1. Click a row in the table. The Delete / Cancel button becomes clickable.
2. Click the button. `deleteSelected()` then:
   - asks "Are you sure?",
   - reads the ID from the first column of the row,
   - calls `model.deleteShow(id)` or `model.cancelBooking(id)`.
3. `refresh()` updates the screen.

## Required features: where to find them

| Requirement | Where in the code |
|---|---|
| Visual seat selection matrix | `seats[]` grid in `BookingView`, `openShow()` and `updateTotal()` in the Controller |
| Showtime scheduling | `addShow()` and `deleteSelected()`, plus the UNIQUE rule on `shows` |
| Ticket pricing | `seatPrice()` in the Model, `updateTotal()` in the Controller |
| Customer booking database | the `bookings` table, `bookSeat()` and `cancelBooking()` |
| MVC + GUI + JDBC | the 3 classes, Swing in the View, `DriverManager` and `PreparedStatement` in the Model |
