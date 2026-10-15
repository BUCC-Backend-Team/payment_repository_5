package Marketly.Payment.Service.config;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;


@ConfigurationProperties(prefix = "app")
@Validated
public record AppProps(String merchantApiKey, String paystackSecretKey, String callbackUrl,
                       String callbackSecret, int paymentTimeoutSeconds, boolean testMode) {}