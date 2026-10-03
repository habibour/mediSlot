package com.medislot;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MedislotApplication {

	public static void main(String[] args) {
		// Pin the JVM to UTC before any JDBC driver loads. Drivers convert java.sql time types through the default
		// zone, so a developer laptop in UTC+6 would otherwise store LocalTime/LocalDate shifted. Clinic-local
		// wall-clock logic is explicit (app.clinic.zone), never implicit.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(MedislotApplication.class, args);
	}

}
