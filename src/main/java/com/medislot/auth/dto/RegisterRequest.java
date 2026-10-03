package com.medislot.auth.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain a letter and a digit") String password,
        @NotBlank @Size(max = 150) String fullName,
        @Past LocalDate dob,
        @Size(max = 30) String phone,
        @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9-]*$", message = "may contain letters, digits and dashes only") String nationalId) {
}
