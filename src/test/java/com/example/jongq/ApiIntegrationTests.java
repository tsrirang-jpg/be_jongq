package com.example.jongq;

import com.example.jongq.booking.BookingService;
import com.example.jongq.booking.BookingEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {"app.admin.password=TestAdmin123", "app.admin.username=admin"})
@AutoConfigureMockMvc
class ApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired BookingService service;
    @Autowired BookingEvents events;
    @Autowired PlatformTransactionManager transactions;
    @BeforeEach void clearBookings() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL().endsWith("/jongq_test"), "Tests require the isolated jongq_test database");
        }
        jdbc.update("DELETE FROM bookings");
    }
    private String date() { return LocalDate.now(ZoneId.of("Asia/Bangkok")).plusDays(1).toString(); }
    @Test void swaggerDocumentationIsPublicAndContainsBookingApi() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("Jongq Booking API"))
            .andExpect(jsonPath("$.paths['/api/bookings'].post").exists())
            .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists());
        mvc.perform(get("/v3/api-docs/swagger-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value("/v3/api-docs"));
        mvc.perform(get("/swagger-ui/swagger-initializer.js"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/auth/csrf")));
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bookings").contentType("application/json").content(booking("10:00")))
            .andExpect(status().isForbidden());
    }
    private String booking(String time) {
        return "{\"name\":\"Customer\",\"phone\":\"0812345678\",\"date\":\"" + date() + "\",\"time\":\"" + time + "\"}";
    }
    private MockHttpSession login() throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
            .content("{\"username\":\"admin\",\"password\":\"TestAdmin123\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("admin")).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
    @Test void loginSessionAndLogout() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
            .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
        var session = login();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("admin"));
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        assertTrue(jdbc.queryForObject("SELECT password_hash FROM admins WHERE username = 'admin'", String.class).startsWith("$2"));
    }
    @Test void realCsrfTokenAndSessionRotation() throws Exception {
        MvcResult csrfResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var token = (CsrfToken) csrfResult.getRequest().getAttribute(CsrfToken.class.getName());
        var session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        var previousId = session.getId();
        mvc.perform(post("/api/auth/login").session(session).header(token.getHeaderName(), token.getToken())
            .contentType("application/json").content("{\"username\":\"admin\",\"password\":\"TestAdmin123\"}"))
            .andExpect(status().isOk());
        assertNotEquals(previousId, session.getId());
        mvc.perform(get("/api/bookings").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/bookings").session(session).contentType("application/json").content(booking("10:00")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session)
                .header("Origin", "http://localhost:5174")
                .header(token.getHeaderName(), token.getToken()))
            .andExpect(status().isForbidden());
        var freshCsrf = mvc.perform(get("/api/auth/csrf").session(session)
                .header("Origin", "http://localhost:5174"))
            .andExpect(status().isOk()).andReturn();
        var freshToken = (CsrfToken) freshCsrf.getRequest().getAttribute(CsrfToken.class.getName());
        mvc.perform(post("/api/auth/logout").session(session)
                .header("Origin", "http://localhost:5174")
                .header(freshToken.getHeaderName(), freshToken.getToken()))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"));
        assertTrue(session.isInvalid());
    }
    @Test void bookingCrudPrivacyAndValidation() throws Exception {
        mvc.perform(post("/api/bookings").with(csrf()).contentType("application/json").content(booking("10:00")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("waiting"));
        mvc.perform(get("/api/slots").param("date", date())).andExpect(status().isOk())
            .andExpect(jsonPath("$.slots[2].available").value(false)).andExpect(jsonPath("$.slots[2].phone").doesNotExist());
        mvc.perform(post("/api/bookings").with(csrf()).contentType("application/json").content(booking("10:00")))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/bookings").with(csrf()).contentType("application/json").content(booking("08:30")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/bookings").with(csrf()).contentType("application/json").content(booking("10:30").replace("0812345678", "123")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/bookings").with(csrf()).contentType("application/json").content("{"))
            .andExpect(status().isBadRequest());
        var id = jdbc.queryForObject("SELECT id FROM bookings", String.class);
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/bookings/" + id + "/status").with(csrf()).contentType("application/json").content("{\"status\":\"done\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/bookings/" + id).with(user("customer").roles("USER")).with(csrf())).andExpect(status().isForbidden());
        var session = login();
        mvc.perform(get("/api/bookings").session(session).param("date", date())).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Customer"));
        mvc.perform(patch("/api/bookings/" + id + "/status").session(session).with(csrf()).contentType("application/json").content("{\"status\":\"invalid\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/bookings/" + id + "/status").session(session).with(csrf()).contentType("application/json").content("{\"status\":\"done\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("done"));
        mvc.perform(delete("/api/bookings/" + id).session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/bookings/" + id).session(session).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/slots").param("date", date())).andExpect(jsonPath("$.slots[2].available").value(true));
    }
    @Test void newOpeningAndClosingSlotsAreAcceptedByTheDatabase() throws Exception {
        mvc.perform(post("/api/bookings").with(csrf()).contentType("application/json").content(booking("09:00")))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/bookings").with(csrf()).contentType("application/json").content(booking("18:00")))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/slots").param("date", date())).andExpect(status().isOk())
            .andExpect(jsonPath("$.slots.length()").value(19))
            .andExpect(jsonPath("$.slots[0].time").value("09:00"))
            .andExpect(jsonPath("$.slots[18].time").value("18:00"))
            .andExpect(jsonPath("$.slots[0].booked").value(true))
            .andExpect(jsonPath("$.serverNow").exists());
    }
    @Test void differentDaysAndPastDate() throws Exception {
        mvc.perform(post("/api/bookings").with(csrf()).contentType("application/json").content(booking("10:00"))).andExpect(status().isCreated());
        var otherDate = LocalDate.parse(date()).plusDays(1).toString();
        mvc.perform(post("/api/bookings").with(csrf()).contentType("application/json").content(booking("10:00").replace(date(), otherDate)))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/bookings").with(csrf()).contentType("application/json").content(booking("10:30").replace(date(), "2000-01-01")))
            .andExpect(status().isBadRequest());
    }
    @Test void loginThrottlingAndCors() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").with(csrf()).with(request -> { request.setRemoteAddr("192.0.2.10"); return request; })
                .contentType("application/json").content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").with(csrf()).with(request -> { request.setRemoteAddr("192.0.2.10"); return request; })
            .contentType("application/json").content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
            .andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/slots").header("Origin", "http://localhost:5173"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(get("/api/slots").header("Origin", "https://untrusted.example"))
            .andExpect(status().isForbidden());
    }
    @Test void sseNotifiesOnlyCommittedChangesWithoutCustomerData() throws Exception {
        var stream = mvc.perform(get("/api/booking-events"))
            .andExpect(status().isOk()).andExpect(request().asyncStarted())
            .andExpect(header().string("X-Accel-Buffering", "no")).andReturn();
        try {
            assertTrue(stream.getResponse().getContentAsString().contains("event:connected"));
            var booking = service.create("Private SSE customer", "0812345678", LocalDate.parse(date()), "12:00");
            assertEquals(1, eventCount(stream));
            assertTrue(stream.getResponse().getContentAsString().contains(date()));
            assertFalse(stream.getResponse().getContentAsString().contains("Private SSE customer"));
            assertFalse(stream.getResponse().getContentAsString().contains("0812345678"));
            assertThrows(DuplicateKeyException.class, () -> service.create("Duplicate", "0812345678", LocalDate.parse(date()), "12:00"));
            assertEquals(1, eventCount(stream));
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                service.create("Rolled back", "0891234567", LocalDate.parse(date()), "12:30");
                transaction.setRollbackOnly();
            });
            assertEquals(1, eventCount(stream));
            service.update(booking.id(), "waiting");
            assertEquals(1, eventCount(stream));
            service.update(booking.id(), "cutting");
            assertEquals(2, eventCount(stream));
            service.delete(booking.id());
            assertEquals(3, eventCount(stream));
            events.heartbeat();
            assertEquals(3, eventCount(stream));
        } finally {
            events.close();
            mvc.perform(asyncDispatch(stream)).andExpect(status().isOk());
        }
    }
    private int eventCount(MvcResult stream) throws Exception {
        return stream.getResponse().getContentAsString().split("event:booking-changed", -1).length - 1;
    }
    @Test void concurrentBookingHasOnlyOneWinner() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            start.await();
            try { service.create("Concurrent", "0891234567", LocalDate.parse(date()), "14:00"); return true; }
            catch (DuplicateKeyException expected) { return false; }
        };
        try {
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            assertEquals(1, List.of(first.get(), second.get()).stream().filter(Boolean::booleanValue).count());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM bookings", Integer.class));
        } finally { executor.shutdownNow(); }
    }
}
