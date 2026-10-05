package com.invictus.platform.config;

import com.invictus.platform.entity.UserAccount;
import com.invictus.platform.repository.UserRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates / updates the single admin account from ADMIN_EMAIL and ADMIN_PASSWORD. */
@Component
public class AdminSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminSeeder(UserRepository users, PasswordEncoder encoder,
                       @Value("${app.admin.email:}") String email,
                       @Value("${app.admin.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.email = email == null ? "" : email.trim().toLowerCase();
        this.password = password == null ? "" : password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isEmpty() || password.isEmpty()) {
            log.warn("ADMIN_EMAIL / ADMIN_PASSWORD not set: no admin account will be available");
            return;
        }
        UserAccount existing = users.findByEmailIgnoreCase(email).orElse(null);
        if (existing == null) {
            UserAccount admin = new UserAccount();
            admin.email = email;
            admin.passwordHash = encoder.encode(password);
            admin.role = "ADMIN";
            admin.createdAt = Instant.now();
            users.save(admin);
            log.info("Admin account created");
        } else if ("ADMIN".equals(existing.role)) {
            if (!encoder.matches(password, existing.passwordHash)) {
                existing.passwordHash = encoder.encode(password);
                users.save(existing);
                log.info("Admin password updated from environment");
            }
        } else {
            log.error("ADMIN_EMAIL belongs to a player account; refusing to promote it");
        }
    }
}
