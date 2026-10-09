package com.example.jongq.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class BookingController {
    private final BookingService service;
    public BookingController(BookingService service) { this.service = service; }
    public record CreateRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Pattern(regexp = "0[689][0-9]{8}") String phone,
                                LocalDate date, @NotBlank String time) {}
    public record StatusRequest(@NotBlank @Pattern(regexp = "waiting|cutting|done") String status) {}
    @GetMapping("/health") Map<String, String> health() { return Map.of("status", "ok"); }
    @GetMapping("/slots") BookingService.Availability slots(@RequestParam(required = false) LocalDate date) { return service.availability(date); }
    @GetMapping("/bookings") List<Booking> list(@RequestParam(required = false) LocalDate date) { return service.list(date); }
    @PostMapping("/bookings") @ResponseStatus(HttpStatus.CREATED)
    Booking create(@Valid @RequestBody CreateRequest request) { return service.create(request.name(), request.phone(), request.date(), request.time()); }
    @PatchMapping("/bookings/{id}/status") Booking update(@PathVariable String id, @Valid @RequestBody StatusRequest request) { return service.update(id, request.status()); }
    @DeleteMapping("/bookings/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable String id) { service.delete(id); }
}