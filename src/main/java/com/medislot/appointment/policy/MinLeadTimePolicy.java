package com.medislot.appointment.policy;

import java.time.Duration;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.medislot.common.exception.PolicyViolationException;

@Component
@Order(2)
public class MinLeadTimePolicy implements SlotPolicy {

    public static final String NAME = "MIN_LEAD_TIME";
    static final Duration MIN_LEAD = Duration.ofMinutes(30);

    @Override
    public void check(BookingContext ctx) {
        if (ctx.start().isBefore(ctx.now().plus(MIN_LEAD))) {
            throw new PolicyViolationException(NAME,
                    "Appointments must be booked at least " + MIN_LEAD.toMinutes() + " minutes ahead");
        }
    }
}
