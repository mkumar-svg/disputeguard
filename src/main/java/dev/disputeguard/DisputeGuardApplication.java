package dev.disputeguard;

import java.net.URI;
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
        useHostedDatabaseUrl(System.getenv("DATABASE_URL"));
        SpringApplication.run(DisputeGuardApplication.class, args);
    }

    /**
     * Hosts such as Render give the database as postgres://user:pass@host:port/db, while JDBC needs
     * jdbc:postgresql://host:port/db plus a separate user and password. Convert when needed.
     */
    static void useHostedDatabaseUrl(String url) {
        if (url == null || !(url.startsWith("postgres://") || url.startsWith("postgresql://"))) {
            return;
        }
        URI uri = URI.create(url);
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        System.setProperty("spring.datasource.url",
                "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath());
        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            System.setProperty("spring.datasource.username", colon < 0 ? userInfo : userInfo.substring(0, colon));
            if (colon >= 0) {
                System.setProperty("spring.datasource.password", userInfo.substring(colon + 1));
            }
        }
    }
}
