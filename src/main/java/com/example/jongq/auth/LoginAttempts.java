package com.example.jongq.auth;

import com.example.jongq.common.ApiException;
import java.util.LinkedHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LoginAttempts {
    private record Attempts(int count, long resetAt) {}
    private final LinkedHashMap<String, Attempts> attempts = new LinkedHashMap<>();
    public synchronized void check(String address) {
        var entry = attempts.get(address);
        if (entry != null && entry.resetAt() > System.currentTimeMillis() && entry.count() >= 5)
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "เข้าสู่ระบบผิดหลายครั้ง กรุณารอ 1 นาทีแล้วลองอีกครั้ง");
    }
    public synchronized void failure(String address) {
        var now = System.currentTimeMillis();
        attempts.entrySet().removeIf(entry -> entry.getValue().resetAt() <= now);
        if (attempts.size() >= 4096) attempts.remove(attempts.keySet().iterator().next());
        var entry = attempts.get(address);
        attempts.put(address, entry == null ? new Attempts(1, now + 60_000) : new Attempts(entry.count() + 1, entry.resetAt()));
    }
    public synchronized void success(String address) { attempts.remove(address); }
}
