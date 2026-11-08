# Code Guide

This guide explains how the Movie Ticket Booking app (CineSeat) works.
You only need basic Java (classes, methods, `if`, loops, arrays) to follow it.

## Files

```
├── CODE_GUIDE.md                  ← this file
└── MovieTicketBooking/
    ├── MovieBookingApp.java       ← the program starts here (main method)
    ├── BookingModel.java          ← Model: the database code
    ├── BookingView.java           ← View: the window (GUI) and all popups
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
| **View** | `BookingView.java` | Draws the window and popups | Buttons, show cards, seat grid, tables |
| **Controller** | `BookingController.java` | Connects the two | "Book clicked → check input → tell Model to save → update View" |

Why split it? Each file stays small, and you always know where to look:
- The Model has **no buttons**.
- The View has **no SQL**.
- The Controller is the only part that uses both.

## Who can do what

| User | How to log in | What they can do |
|---|---|---|
| Anyone (not logged in) | - | See the show cards. Clicking a card opens the login popup first |
| Customer | Any 10-digit phone number + a password. A new number creates an account automatically | Book seats, see **Your Bookings** under the cards |
| Admin | Phone `1234567890`, password `movie` (hardcoded in the Controller) | Everything above, plus the **Manage Shows** and **Bookings** tabs |

A wrong password shows **"Wrong Credentials"**.
Names are not unique, so the name is asked for **each booking**, not at login.

## What happens when the app starts

1. `main()` in `MovieBookingApp.java` runs.
2. `BookingView.setTheme()` sets the fonts and colours.
3. The Model, View and Controller objects are created.
4. The Controller's constructor tells each button which method to run when it's clicked.
5. `startApp()`:
   - creates the database tables (and 3 sample shows for tomorrow, the first time),
   - loads the data into the window,
   - shows the window. Nobody is logged in, so only the **Shows** tab is visible.

## The database

The data is stored in `movie_booking.db`, a **SQLite** database (a single file, no server needed).
Java talks to it through **JDBC**.

**Table `shows`**: one row per show.

| id | movie | show_date | show_time | screen | price |
|---|---|---|---|---|---|
| 1 | Inception | 2026-11-09 | 10:00 | Screen 1 | 180 |

**Table `bookings`**: one row per booked **seat**. Booking 3 seats = 3 rows.

| id | show_id | seat | name | phone | price |
|---|---|---|---|---|---|
| 1 | 1 | A1 | Rahul | 9876543210 | 180 |

- `show_id` says which show the seat belongs to (it matches `shows.id`).
- `phone` says which account made the booking (it matches `users.phone`).

**Table `users`**: one row per customer account.

| phone | password |
|---|---|
| 9876543210 | rahul123 |

- The admin is **not** in this table. Its phone and password are constants in the Controller.

**Rules the database checks for us**:
- `UNIQUE(screen, show_date, show_time)`: a screen can't have 2 shows at the same date and time.
- `UNIQUE(show_id, seat)`: the same seat can't be booked twice for one show.
- `phone PRIMARY KEY` in `users`: one account per phone number.

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
| `(SELECT COUNT(*) ...)` | A query inside a query. Used to count the booked seats of each show |

## 1. Model: `BookingModel.java`

| Method | What it does |
|---|---|
| `connect()` | Opens the database file |
| `exec(sql, values...)` | Runs SQL that changes data (INSERT, DELETE, CREATE) |
| `query(sql, values...)` | Runs a SELECT and returns the rows as a list of `String[]` |
| `createTables()` | Creates the 3 tables, and adds 3 sample shows for tomorrow if there are none |
| `getShows()` | All shows, earliest first, with the number of seats booked |
| `getBookings()` | All bookings, newest first, with the movie name, date and time |
| `getUserBookings(phone)` | Only the bookings of one customer (for "Your Bookings") |
| `getPassword(phone)` | The saved password for a phone number, or `null` if there's no account yet |
| `addUser(phone, password)` | Creates a new account |
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

**Header** (top of the window): the logo and "CineSeat" on the left; "Hi, …" and the **Login / Logout** button on the right.

**The tabs**:
1. **Shows** (everyone) has 2 pages. Only one is visible at a time:
   - `"shows"`: one card per show, and **Your Bookings** below the cards when a customer is logged in.
   - `"seats"`: the 6 × 10 seat grid, the total price and the **Continue** button.
2. **Manage Shows** (admin only): a table of all shows with **+ Add Show** and **Delete Selected**.
3. **Bookings** (admin only): a table of all bookings with **Cancel Selected**.

`setUser(name, admin)` adds the 2 admin tabs when the admin logs in and removes them on logout.

**Seat colours**:
- Light grey: available.
- Red: selected by you.
- White with grey text: already booked (the button is disabled).

**Show cards**: each card is a `ShowCard` (a small class inside `BookingView`) that draws itself:
a coloured "poster" with the movie name and screen, the date and time, how many seats are left,
the starting price and a **Book** button. Moving the mouse over a card lifts it up with a red outline.
A sold-out card turns grey and can't be clicked.

**Logo**: the `Logo` class (also inside `BookingView`) draws the red ticket logo with shapes instead of
loading a picture, so it stays sharp at any screen size.

### Popups

Every popup is built by the same method, `popup()`, so they all look and work the same way:

```
+------------------------------------+
| Title                              |
| +--------------------------------+ |
| | grey summary box               | |
| +--------------------------------+ |
|  text boxes (only in form popups)  |
|  red error line                    |
|               [Cancel] [Confirm]   |
+------------------------------------+
```

In the **form popups** (login, booking name, add show), the Controller checks what was typed when the
main button is clicked. If something is wrong, the popup **stays open** and shows the problem in red,
so the user can fix it. If everything is fine, the popup closes.

### Swing parts used

| Swing class | What it is | Used for |
|---|---|---|
| `JFrame` | A window | The main window (`BookingView extends JFrame`) |
| `JTabbedPane` | Tabs | Shows, Manage Shows, Bookings |
| `CardLayout` | Shows one panel at a time, like a deck of cards | Switching between the "shows" and "seats" pages |
| `JToggleButton` | A button that stays pressed in | Seats (pressed = selected) |
| `JTable` + `DefaultTableModel` | A table + the data inside it | Shows, Bookings and Your Bookings tables |
| `JDialog` | A popup window | Login, booking, add show, confirmations, messages |
| `JTextField` / `JPasswordField` | Text boxes (the password box hides what you type) | All form popups |
| `JPanel` + `paintComponent()` | A panel that draws itself with `Graphics2D` | The show cards |
| `Icon` | Something that can be drawn as an icon | The logo |
| `GridLayout`, `BorderLayout`, `FlowLayout` | Ways to arrange components | Grid of seats and cards, top/middle/bottom, a row |
| `UIManager` | Sets the default look of all components | `setTheme()` |

### Methods the Controller calls

| Method | What it does |
|---|---|
| `setTheme()` | Sets fonts and colours for the whole app (runs before the window is built) |
| `fillCards(shows, onClick)` | Makes one `ShowCard` per show |
| `showPage(name)` | Switches to the `"shows"` or `"seats"` page |
| `setUser(name, admin)` | Updates the header, shows/hides Your Bookings, adds/removes the admin tabs |
| `askLogin(onConfirm)` | Login popup (phone + password). Returns what was typed, or `null` if cancelled |
| `askName(...)` | Booking popup with a summary and a name box. Returns `{name}`, or `null` |
| `askShow(onConfirm)` | Add a New Show popup. Returns `{movie, screen, date, time, price}`, or `null` |
| `askConfirm(...)` | "Are you sure?" popup with the details. Returns `true` if confirmed |
| `showMessage(title, heading, lines...)` | Message popup with an OK button |
| `fillTable(table, rows)` | Replaces all rows in a table |
| `niceDate(date)` / `niceTime(time)` | Turns `2026-11-09` into `Mon, 9 Nov` and `18:00` into `6:00 PM` |

The other private methods (`button`, `chip`, `summary`, `heading`, `table`, `row`, `fill`, `layout`,
`tableSection`, `field`, `border`) are small helpers. They create styled components so the same styling
code isn't repeated everywhere.

## 3. Controller: `BookingController.java`

| Method | When it runs | What it does |
|---|---|---|
| constructor | App start | Connects each button to its method |
| `startApp()` | App start | Creates tables, loads data, shows the window |
| `refresh()` | After every change | Reloads the cards, the tables and Your Bookings from the database |
| `loginOrLogout()` | Login / Logout is clicked | Logs out, or opens the login popup |
| `checkLogin(typed)` | Login is clicked in the popup | Checks the phone and password (see below) |
| `isAdmin()` | Used by other methods | `true` if the admin is logged in |
| `openShow(show)` | A show card is clicked | Asks to log in first if needed, disables booked seats, opens the seats page |
| `getSelectedSeats()` | Used by other methods | The seats you've clicked, e.g. `["A1", "A2"]` |
| `updateTotal()` | A seat is clicked | Adds up the seat prices and shows the total |
| `bookTickets()` | Continue is clicked | Checks seats, asks the name, saves each seat |
| `checkName(typed)` | Confirm Booking is clicked | Checks the name isn't empty |
| `addShow()` / `saveShow(s)` | "+ Add Show" is clicked | Opens the popup / checks every field and saves the show |
| `deleteShow()` | Delete Selected is clicked | Shows the details, then deletes the show and its bookings |
| `cancelBooking()` | Cancel Selected is clicked | Shows the details, then deletes the booking |
| `showError(e)` | A database error happens | Shows the error in a popup |

### Java terms used in the Controller

| Code | Meaning |
|---|---|
| `button.addActionListener(e -> bookTickets())` | "When this button is clicked, run `bookTickets()`". `e -> ...` is a **lambda**, a short way to write a small method |
| `this::openShow` | Passes the `openShow` method itself, so the View can call it when a card is clicked |
| `view.askLogin(this::checkLogin)` | The popup calls `checkLogin()` when Login is clicked. It returns an error message, or `null` if all is OK |
| `text.matches("[0-9]{10}")` | Checks the text against a pattern (**regular expression**). `[0-9]{10}` means exactly 10 digits |
| `LocalDate.parse("2026-02-30")` | Turns text into a real date. Throws a `DateTimeParseException` if the date doesn't exist |
| `try { } catch (SQLException e) { }` | If the database gives an error, show a message instead of crashing |

## Step by step: logging in

1. Click **Login** (or click a show card while logged out).
2. Type a phone number and password, then click **Login**. `checkLogin()` then:
   - checks the phone has 10 digits and the password isn't empty,
   - if the phone is the admin's, checks the admin password,
   - otherwise asks the Model for the saved password:
     - none saved → a new account is created,
     - different password → "Wrong Credentials" (the popup stays open).
3. `view.setUser()` shows "Hi, …", and the admin gets the 2 extra tabs.
4. `refresh()` loads Your Bookings.

## Step by step: booking tickets

1. You click a show card.
   - If nobody is logged in, the login popup opens first.
   - `openShow()` asks the Model for the booked seats and disables them.
   - The seats page opens.
2. You click some seats.
   - Each click runs `updateTotal()`, which updates the total price.
3. You click **Continue**. `bookTickets()` then:
   - checks that at least one seat is selected,
   - opens the popup with the booking summary and asks the name for this booking,
   - calls `model.bookSeat()` once for each seat, with that name and your phone number,
   - shows a "Booking Confirmed" message.
4. `refresh()` reloads everything. You're back on the show cards, and the seats appear under **Your Bookings**.

## Step by step: adding a show (admin)

1. On the **Manage Shows** tab, click **+ Add Show** and fill in the popup.
2. `saveShow()` checks each field:
   - the movie and screen aren't empty,
   - the date looks like `2026-11-05`, is a real date and isn't in the past,
   - the time looks like `18:00` and is a real time,
   - the price is a number above 0.
3. `model.addShow()` saves it.
   - If that screen already has a show at that time, the database refuses it.
   - You see "This screen already has a show at that date and time."
4. Any problem is shown in red and the popup stays open. When it's saved, you see "Show Added",
   and the new show appears in the table and as a card.

## Step by step: deleting a show or cancelling a booking (admin)

1. Click a row in the table. The Delete / Cancel button becomes clickable.
2. Click the button. A popup shows the details (and how many booked seats will be cancelled with a show).
3. Click **Delete Show** / **Cancel Booking** (or **Go Back** to keep it).
4. `model.deleteShow(id)` or `model.cancelBooking(id)` runs, and `refresh()` updates the screen.

## Required features: where to find them

| Requirement | Where in the code |
|---|---|
| Visual seat selection matrix | `seats[]` grid in `BookingView`, `openShow()` and `updateTotal()` in the Controller |
| Showtime scheduling | `addShow()`, `saveShow()` and `deleteShow()`, plus the UNIQUE rule on `shows` |
| Ticket pricing | `seatPrice()` in the Model, `updateTotal()` in the Controller |
| Customer booking database | the `bookings` table, `bookSeat()`, `getUserBookings()` and `cancelBooking()` |
| Login / admin | the `users` table, `loginOrLogout()`, `checkLogin()` and `setUser()` |
| MVC + GUI + JDBC | the 3 classes, Swing in the View, `DriverManager` and `PreparedStatement` in the Model |
