package com.example.jongq.booking;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class BookingEventsController {
    private final BookingEvents events;
    public BookingEventsController(BookingEvents events) { this.events = events; }
    @GetMapping(value = "/api/booking-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header("X-Accel-Buffering", "no").body(events.connect());
    }
}