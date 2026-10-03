package com.medislot.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Cross-cutting performance guard: warns when a @Service method is slow. */
@Aspect
@Component
public class TimingAspect {

    private static final Logger log = LoggerFactory.getLogger(TimingAspect.class);
    static final long SLOW_MS = 200;

    @Around("within(@org.springframework.stereotype.Service *) && !within(com.medislot.audit.*)")
    public Object time(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (ms > SLOW_MS) {
                log.warn("Slow service call {} took {} ms", pjp.getSignature().toShortString(), ms);
            }
        }
    }
}
