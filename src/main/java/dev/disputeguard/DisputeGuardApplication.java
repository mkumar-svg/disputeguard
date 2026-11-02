package dev.disputeguard;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DisputeGuardApplication {

    public static void main(String[] args) {
        // Windows default zone "Asia/Calcutta" is rejected by Postgres; run in UTC instead.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(DisputeGuardApplication.class, args);
    }
}