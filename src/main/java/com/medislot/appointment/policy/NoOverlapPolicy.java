package com.medislot.appointment.policy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.medislot.appointment.AppointmentRepository;
import com.medislot.appointment.AppointmentStatus;
import com.medislot.common.exception.SlotTakenException;

/** Relies on the caller holding row locks on doctor and patient so the check cannot race. */
@Component
@Order(3)
public class NoOverlapPolicy implements SlotPolicy {

    private final AppointmentRepository appointments;

    public NoOverlapPolicy(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Override
    public void check(BookingContext ctx) {
        if (appointments.existsByDoctorIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
                ctx.doctor().getId(), AppointmentStatus.BOOKED, ctx.end(), ctx.start())) {
            throw new SlotTakenException("The doctor is already booked for that time");
        }
        if (appointments.existsByPatientIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
                ctx.patient().getId(), AppointmentStatus.BOOKED, ctx.end(), ctx.start())) {
            throw new SlotTakenException("You already have an appointment overlapping that time");
        }
    }
}
