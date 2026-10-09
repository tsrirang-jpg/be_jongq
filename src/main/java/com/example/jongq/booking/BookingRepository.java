package com.example.jongq.booking;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<Booking> mapper = (rs, row) -> new Booking(rs.getString("id"), rs.getString("name"),
        rs.getString("phone"), rs.getObject("booking_date", LocalDate.class), rs.getString("booking_time"), rs.getString("status"));
    public BookingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Booking> findByDate(LocalDate date) {
        return jdbc.query("SELECT * FROM bookings WHERE booking_date = ? ORDER BY booking_time", mapper, date);
    }
    public List<String> occupiedTimes(LocalDate date) {
        return jdbc.query("SELECT booking_time FROM bookings WHERE booking_date = ?", (rs, row) -> rs.getString(1), date);
    }
    public void insert(Booking booking) {
        jdbc.update("INSERT INTO bookings(id, name, phone, booking_date, booking_time, status) VALUES (?, ?, ?, ?, ?, ?)",
            booking.id(), booking.name(), booking.phone(), booking.date(), booking.time(), booking.status());
    }
    public Booking find(String id) {
        return jdbc.query("SELECT * FROM bookings WHERE id = ?", mapper, id).stream().findFirst().orElse(null);
    }
    public int updateStatus(String id, String status) { return jdbc.update("UPDATE bookings SET status = ? WHERE id = ?", status, id); }
    public int delete(String id) { return jdbc.update("DELETE FROM bookings WHERE id = ?", id); }
}