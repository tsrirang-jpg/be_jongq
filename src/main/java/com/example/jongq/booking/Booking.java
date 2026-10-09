package com.example.jongq.booking;

import java.time.LocalDate;

public record Booking(String id, String name, String phone, LocalDate date, String time, String status) {}