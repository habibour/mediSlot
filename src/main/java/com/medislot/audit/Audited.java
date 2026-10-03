package com.medislot.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose successful invocation must leave an audit trail.
 * {@code idArg} is the index of the method argument that holds the accessed resource id.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    String action();

    String resource();

    int idArg() default -1;
}
