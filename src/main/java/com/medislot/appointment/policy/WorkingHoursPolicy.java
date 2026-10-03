package com.medislot.appointment.policy;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.medislot.common.exception.PolicyViolationException;
import com.medislot.config.ClinicProperties;
import com.medislot.doctor.Doctor;

/** Slot must sit on the doctor's slot grid and finish inside working hours (clinic-local time). */
@Component
@Order(1)
public class WorkingHoursPolicy implements SlotPolicy {

    public static final String NAME = "WORKING_HOURS";

    private final ClinicProperties clinic;

    public WorkingHoursPolicy(ClinicProperties clinic) {
        this.clinic = clinic;
    }

    @Override
    public void check(BookingContext ctx) {
        Doctor doctor = ctx.doctor();
        ZonedDateTime start = ctx.start().atZone(clinic.zone());
        ZonedDateTime end = ctx.end().atZone(clinic.zone());
        LocalTime startTime = start.toLocalTime();

        boolean sameDay = start.toLocalDate().equals(end.toLocalDate());
        boolean insideHours = !startTime.isBefore(doctor.getWorkingStart())
                && !end.toLocalTime().isAfter(doctor.getWorkingEnd());
        if (!sameDay || !insideHours) {
            throw new PolicyViolationException(NAME, "Slot is outside the doctor's working hours ("
                    + doctor.getWorkingStart() + "-" + doctor.getWorkingEnd() + ")");
        }

        long minutesFromOpen = Duration.between(doctor.getWorkingStart(), startTime).toMinutes();
        boolean aligned = startTime.getSecond() == 0 && startTime.getNano() == 0
                && minutesFromOpen % doctor.getSlotMinutes() == 0;
        if (!aligned) {
            throw new PolicyViolationException(NAME, "Start time must align to the doctor's "
                    + doctor.getSlotMinutes() + "-minute slot grid");
        }
    }
}
