package com.github.svenfran.budgetapp.budgetappbackend.service;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.mail.Session;
import javax.mail.Transport;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class HealthCheckService {

    private final JdbcTemplate jdbcTemplate;
    private static final Long DISK_THRESHOLD = 10L * 1024 * 1024; // 10 MB
    private static final String EMAIL_HOST = System.getenv("email_host");
    private static final String EMAIL_PORT = System.getenv("email_port");
    private static final String EMAIL_USERNAME = System.getenv("email_username");
    private static final String EMAIL_PASSWORD = System.getenv("email_password");
    private static final String ERROR = "error";

    public HealthCheckService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Führt alle Health-Checks aus und gibt die Ergebnisse zurück.
     */
    public Map<String, Object> checkHealth() {
        Map<String, Health> healthResults = new HashMap<>();

        healthResults.put("db", checkDatabase());
        healthResults.put("diskSpace", checkDiskSpace());
        healthResults.put("mail", checkMailServer());
        healthResults.put("ping", checkPing());

        // Bestimme den Gesamtstatus
        Status overallStatus = determineOverallStatus(healthResults);

        // Ergebnis zusammenstellen
        Map<String, Object> response = new HashMap<>();
        response.put("status", overallStatus);
        response.put("components", healthResults);

        return response;
    }

    /**
     * Health-Check für die Datenbank
     */
    private Health checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Health.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("validationQuery", "SELECT 1")
                    .build();
        } catch (Exception e) {
            return Health.down().withDetail(ERROR, e.getMessage()).build();
        }
    }

    /**
     * Health-Check für den Speicherplatz
     */
    private Health checkDiskSpace() {
        File disk = new File("/");
        long total = disk.getTotalSpace();
        long free = disk.getFreeSpace();

        return free > DISK_THRESHOLD ? Health.up()
                .withDetail("total", total)
                .withDetail("free", free)
                .withDetail("threshold", DISK_THRESHOLD)
                .withDetail("exists", disk.exists())
                .build()
                : Health.down()
                .withDetail(ERROR, "Low disk space")
                .build();
    }

    /**
     * Health-Check für den Mailserver (SMTP)
     */
    private Health checkMailServer() {
        Properties props = new Properties();
        props.put("mail.smtp.host", EMAIL_HOST);
        props.put("mail.smtp.port", EMAIL_PORT);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        try {
            Session session = Session.getInstance(props);
            Transport transport = session.getTransport("smtp");
            transport.connect(
                    EMAIL_HOST,
                    Integer.parseInt(EMAIL_PORT),
                    EMAIL_USERNAME,
                    EMAIL_PASSWORD
            );
            transport.close();

            return Health.up().withDetail("location", EMAIL_HOST + ":" + EMAIL_PORT).build();
        } catch (Exception e) {
            return Health.down().withDetail(ERROR, e.getMessage()).build();
        }
    }

    /**
     * Health-Check für den Ping (Erreichbarkeit des Servers)
     */
    private Health checkPing() {
        try {
            boolean reachable = InetAddress.getByName("127.0.0.1").isReachable(1000);
            return reachable ? Health.up().build() : Health.down().build();
        } catch (IOException e) {
            return Health.down().withDetail(ERROR, e.getMessage()).build();
        }
    }

    private Status determineOverallStatus(Map<String, Health> healthResults) {
        return healthResults.values().stream()
                .allMatch(health -> health.getStatus().equals(Status.UP)) ? Status.UP : Status.DOWN;
    }
}
