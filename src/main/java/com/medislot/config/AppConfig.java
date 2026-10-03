package com.medislot.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.medislot.common.crypto.EncryptionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ClinicProperties.class, EncryptionProperties.class})
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
