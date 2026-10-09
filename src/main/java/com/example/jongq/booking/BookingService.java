package com.example.jongq.booking;

import com.example.jongq.common.ApiException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    public static final List<String> TIMES = List.of("10:00", "10:30", "11:00", "11:30", "12:00", "12:30", "13:00", "13:30", "14:00");
    private final BookingRepository repository;
    private final ZoneId zone;
    private final ApplicationEventPublisher events;
    public BookingService(BookingRepository repository, @Value("${app.time-zone}") String zone, ApplicationEventPublisher events) {
        this.repository = repository; this.zone = ZoneId.of(zone); this.events = events;
    }
    public LocalDate resolveDate(LocalDate date) { return date == null ? LocalDate.now(zone) : date; }
    public record Slot(String time, boolean available) {}
    public record Availability(LocalDate date, List<Slot> slots) {}
    public Availability availability(LocalDate requested) {
        var date = resolveDate(requested);
        var occupied = repository.occupiedTimes(date);
        return new Availability(date, TIMES.stream().map(time -> new Slot(time, !occupied.contains(time))).toList());
    }
    public List<Booking> list(LocalDate date) { return repository.findByDate(resolveDate(date)); }
    @Transactional public Booking create(String name, String phone, LocalDate requested, String time) {
        var date = resolveDate(requested);
        if (date.isBefore(LocalDate.now(zone)) || !TIMES.contains(time)) throw new ApiException(HttpStatus.BAD_REQUEST, "วันที่หรือเวลาไม่ถูกต้อง");
        var booking = new Booking(UUID.randomUUID().toString(), name.trim(), phone, date, time, "waiting");
        repository.insert(booking); // UNIQUE(date,time) arbitrates concurrent bookings atomically.
        events.publishEvent(new BookingChanged(date));
        return booking;
    }
    @Transactional public Booking update(String id, String status) {
        var existing = findRequired(id);
        if (existing.status().equals(status)) return existing;
        if (repository.updateStatus(id, status) == 0) throw new ApiException(HttpStatus.NOT_FOUND, "ไม่พบคิวนี้");
        events.publishEvent(new BookingChanged(existing.date()));
        return repository.find(id);
    }
    private Booking findRequired(String id) {
        var booking = repository.find(id);
        if (booking == null) throw new ApiException(HttpStatus.NOT_FOUND, "ไม่พบคิวนี้");
        return booking;
    }
    @Transactional public void delete(String id) {
        var existing = findRequired(id);
        if (repository.delete(id) == 0) throw new ApiException(HttpStatus.NOT_FOUND, "ไม่พบคิวนี้");
        events.publishEvent(new BookingChanged(existing.date()));
    }
}