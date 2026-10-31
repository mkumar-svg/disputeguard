package dev.disputeguard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** PayPal REST credentials and webhook settings, bound from the {@code paypal.*} properties. */
@ConfigurationProperties(prefix = "paypal")
public record PayPalProperties(
        String baseUrl,
        String clientId,
        String clientSecret,
        String webhookId,
        boolean skipWebhookVerification) {
}
