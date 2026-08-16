package com.patrick.fintech.loan_backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.production")
public class ProductionProperties {
    private boolean strict = true;

    @NotBlank
    private String environment = "development";

    @NotBlank
    private String jwtSecret = "";

    @Positive
    private long maxUploadBytes = 10 * 1024 * 1024L;

    @Positive
    private int maxImportRows = 10_000;
}
