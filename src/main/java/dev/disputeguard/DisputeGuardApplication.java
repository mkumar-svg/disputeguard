package dev.disputeguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DisputeGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(DisputeGuardApplication.class, args);
    }
}
