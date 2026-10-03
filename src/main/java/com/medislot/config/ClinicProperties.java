package com.medislot.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Working hours of doctors are expressed in this zone. */
@ConfigurationProperties(prefix = "app.clinic")
public record ClinicProperties(ZoneId zone) {

    public ClinicProperties {
        if (zone == null) {
            zone = ZoneId.of("Asia/Dhaka");
        }
    }
}
