package com.example.jongq.booking;

import java.time.LocalDate;

// Invalidation only: no customer name, phone, or booking identifier is broadcast.
public record BookingChanged(LocalDate date) {}