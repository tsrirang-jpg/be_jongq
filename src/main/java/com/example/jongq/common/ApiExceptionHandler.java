package com.example.jongq.common;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> api(ApiException e) { return error(e.getStatus(), e.getMessage()); }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, String>> auth() { return error(HttpStatus.UNAUTHORIZED, "ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง"); }
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Map<String, String>> duplicate() { return error(HttpStatus.CONFLICT, "เวลานี้ถูกจองแล้ว กรุณาเลือกเวลาใหม่"); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> invalid() { return error(HttpStatus.BAD_REQUEST, "ข้อมูลไม่ถูกต้อง กรุณาตรวจสอบชื่อ เบอร์โทร วันที่ และเวลา"); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, String>> database(DataAccessException e) {
        log.error("Database request failed", e);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "ฐานข้อมูลไม่พร้อมใช้งาน กรุณาลองอีกครั้ง");
    }
}