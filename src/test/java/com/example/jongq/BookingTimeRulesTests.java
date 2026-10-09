package com.example.jongq;

import com.example.jongq.booking.BookingRepository;
import com.example.jongq.booking.BookingService;
import com.example.jongq.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingTimeRulesTests {
    private static final String ZONE = "Asia/Bangkok";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
    private BookingService service(String instant, BookingRepository repository) {
        return new BookingService(repository, ZONE, mock(ApplicationEventPublisher.class), Clock.fixed(Instant.parse(instant), ZoneId.of(ZONE)));
    }
    @Test void rangeHasNineteenHalfHourlySlots() {
        assertEquals(19, BookingService.TIMES.size());
        assertEquals("09:00", BookingService.TIMES.get(0));
        assertEquals("09:30", BookingService.TIMES.get(1));
        assertEquals("18:00", BookingService.TIMES.get(18));
    }
    @Test void pastSlotsAreUnavailableButFutureSlotsCanBeBooked() {
        var repository = mock(BookingRepository.class);
        when(repository.occupiedTimes(TODAY)).thenReturn(List.of("11:00"));
        var service = service("2026-10-09T03:15:00Z", repository); // 10:15 Bangkok
        var availability = service.availability(null);
        assertEquals(TODAY, availability.date());
        assertEquals(Instant.parse("2026-10-09T03:15:00Z"), availability.serverNow());
        assertFalse(availability.slots().get(2).available()); // 10:00 is past
        assertFalse(availability.slots().get(2).booked());
        assertTrue(availability.slots().get(3).available()); // 10:30 is future
        assertFalse(availability.slots().get(4).available()); // 11:00 is booked
        assertTrue(availability.slots().get(4).booked());
        assertEquals(Instant.parse("2026-10-09T03:30:00Z"), availability.slots().get(3).startsAt());
        assertThrows(ApiException.class, () -> service.create("Customer", "0812345678", TODAY, "10:00"));
        service.create("Customer", "0812345678", TODAY, "10:30");
        verify(repository, times(1)).insert(any());
    }
    @Test void exactBoundaryIsRejectedAndOneNanosecondBeforeIsAllowed() {
        var repository = mock(BookingRepository.class);
        assertThrows(ApiException.class, () -> service("2026-10-09T03:30:00Z", repository).create("Customer", "0812345678", TODAY, "10:30"));
        assertThrows(ApiException.class, () -> service("2026-10-09T03:30:01Z", repository).create("Customer", "0812345678", TODAY, "10:30"));
        service("2026-10-09T03:29:59.999999999Z", repository).create("Customer", "0812345678", TODAY, "10:30");
        verify(repository, times(1)).insert(any());
    }
    @Test void futureDaysAllowOpeningAndClosingTimesAndOutOfHoursAreRejected() {
        var repository = mock(BookingRepository.class);
        var service = service("2026-10-09T13:00:00Z", repository); // 20:00 Bangkok
        assertTrue(service.availability(TODAY).slots().stream().noneMatch(BookingService.Slot::available));
        assertTrue(service.availability(TODAY.plusDays(1)).slots().stream().allMatch(BookingService.Slot::available));
        assertTrue(service.availability(TODAY.minusDays(1)).slots().stream().noneMatch(BookingService.Slot::available));
        service.create("Opening", "0812345678", TODAY.plusDays(1), "09:00");
        service.create("Closing", "0812345678", TODAY.plusDays(1), "18:00");
        assertThrows(ApiException.class, () -> service.create("Too early", "0812345678", TODAY.plusDays(1), "08:30"));
        assertThrows(ApiException.class, () -> service.create("Too late", "0812345678", TODAY.plusDays(1), "18:30"));
        verify(repository, times(2)).insert(any());
    }
}