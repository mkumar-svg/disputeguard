package dev.disputeguard;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DisputeGuardApplication {

    public static void main(String[] args) {
        // Run the JVM in UTC. On Windows the default zone is often "Asia/Calcutta", an old alias
        // that Postgres rejects at connect time ("invalid value for parameter TimeZone").
        // UTC also keeps PayPal timestamps and the database consistent.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(DisputeGuardApplication.class, args);
    }
}
