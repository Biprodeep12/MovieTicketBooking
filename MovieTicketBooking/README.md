# Movie Ticket Booking System

WCA Mini Project: Class Group A1, Team 1.
A simple Java Swing app using **MVC** and **JDBC (SQLite)**, all in `MovieBookingApp.java`:
- **Model** (`BookingModel`): database tables, SQL, seat pricing
- **View** (`BookingView`): Swing window with 3 tabs
- **Controller** (`BookingController`): button actions, input checks, connects Model and View

## Features
- **Seat selection matrix**: rows A–F × seats 1–10. Booked seats are greyed out.
- **Showtime scheduling**: add or delete shows. A screen can't have two shows at the same date and time.
- **Ticket pricing**: rows A–B use the normal price, C–D ×1.25, E–F ×1.5.
- **Booking database**: name, phone, seat and price are saved. A booking can be cancelled. A seat can't be booked twice.

## Requirements
- JDK 8 or newer
- `sqlite-jdbc-3.53.2.1.jar` in this folder

## Compile and Run
**Windows**
```
javac -cp ".;sqlite-jdbc-3.53.2.1.jar" MovieBookingApp.java
java -cp ".;sqlite-jdbc-3.53.2.1.jar" MovieBookingApp
```
**Linux / macOS**
```
javac -cp ".:sqlite-jdbc-3.53.2.1.jar" MovieBookingApp.java
java -cp ".:sqlite-jdbc-3.53.2.1.jar" MovieBookingApp
```
`movie_booking.db` is created on first run with 3 sample shows. Delete it to reset.
