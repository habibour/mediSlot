package com.medislot.appointment.policy;

/** Strategy: one booking rule. Implementations throw a domain exception when the rule is violated. */
public interface SlotPolicy {

    void check(BookingContext ctx);
}
