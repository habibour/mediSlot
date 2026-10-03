package com.medislot.patient.dto;

import java.time.LocalDate;

import com.medislot.common.util.Masking;
import com.medislot.patient.Patient;

public record PatientResponse(Long id, String fullName, LocalDate dob, String phone, String nationalId) {

    /** The national ID is only ever returned masked. */
    public static PatientResponse from(Patient p) {
        return new PatientResponse(p.getId(), p.getFullName(), p.getDob(), p.getPhone(),
                Masking.nationalId(p.getNationalId()));
    }
}
