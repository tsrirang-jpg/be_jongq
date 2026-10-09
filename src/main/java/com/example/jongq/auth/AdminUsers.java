package com.example.jongq.auth;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminUsers implements UserDetailsService, ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;
    public AdminUsers(JdbcTemplate jdbc, PasswordEncoder encoder, @Value("${app.admin.username}") String username,
                      @Value("${app.admin.password}") String password) {
        this.jdbc = jdbc; this.encoder = encoder; this.username = username; this.password = password;
    }
    @Override public UserDetails loadUserByUsername(String username) {
        var users = jdbc.query("SELECT username, password_hash FROM admins WHERE username = ?",
            (rs, row) -> User.withUsername(rs.getString("username")).password(rs.getString("password_hash")).roles("ADMIN").build(), username);
        if (users.isEmpty()) throw new UsernameNotFoundException("Invalid credentials");
        return users.get(0);
    }
    @Override public void run(ApplicationArguments args) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM admins", Integer.class) > 0) return;
        if (password.length() < 8 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 || username.isBlank() || username.length() > 80)
            throw new IllegalStateException("Set ADMIN_USERNAME and ADMIN_PASSWORD (8-72 characters), or use the dev profile for local setup.");
        jdbc.update("INSERT INTO admins(id, username, password_hash) VALUES (?, ?, ?) ON CONFLICT (username) DO NOTHING",
            UUID.randomUUID().toString(), username.trim(), encoder.encode(password));
    }
}