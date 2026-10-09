package com.example.jongq.booking;

import com.example.jongq.common.ApiException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.time.Clock;
import java.util.stream.IntStream;
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
    public static final List<String> TIMES = IntStream.rangeClosed(0, 18)
        .mapToObj(index -> LocalTime.of(9, 0).plusMinutes(index * 30L).toString()).toList();
    private final BookingRepository repository;
    private final ZoneId zone;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    public BookingService(BookingRepository repository, @Value("${app.time-zone}") String zone, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository; this.zone = ZoneId.of(zone); this.events = events; this.clock = clock;
    }
    public LocalDate resolveDate(LocalDate date) { return date == null ? LocalDate.now(clock) : date; }
    public record Slot(String time, boolean available, boolean booked, Instant startsAt) {}
    public record Availability(LocalDate date, Instant serverNow, List<Slot> slots) {}
    public Availability availability(LocalDate requested) {
        var date = resolveDate(requested);
        var occupied = repository.occupiedTimes(date);
        var now = clock.instant();
        return new Availability(date, now, TIMES.stream().map(time -> {
            var startsAt = date.atTime(LocalTime.parse(time)).atZone(zone).toInstant();
            var booked = occupied.contains(time);
            return new Slot(time, !booked && startsAt.isAfter(now), booked, startsAt);
        }).toList());
    }
    public List<Booking> list(LocalDate date) { return repository.findByDate(resolveDate(date)); }
    @Transactional public Booking create(String name, String phone, LocalDate requested, String time) {
        var date = resolveDate(requested);
        if (date.isBefore(LocalDate.now(clock)) || !TIMES.contains(time)) throw new ApiException(HttpStatus.BAD_REQUEST, "วันที่หรือเวลาไม่ถูกต้อง");
        if (!date.atTime(LocalTime.parse(time)).atZone(zone).toInstant().isAfter(clock.instant()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "เวลานี้ผ่านไปแล้ว กรุณาเลือกเวลาที่ยังไม่ถึง");
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