package com.invictus.platform;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InvictusApplication {
    public static void main(String[] args) {
        // Persist and read all timestamps in UTC regardless of the machine zone (e.g. Asia/Kolkata).
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(InvictusApplication.class, args);
    }
}
