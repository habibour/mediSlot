package com.medislot.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Writes RFC 7807 bodies from servlet filters, where @RestControllerAdvice does not apply. */
@Component
class ProblemDetailWriter {

    private final ObjectMapper mapper;

    ProblemDetailWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    void write(HttpServletResponse response, HttpStatus status, String title, String detail) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), pd);
    }
}
