package com.claims.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;

public class NotFutureDateValidator implements ConstraintValidator<NotFutureDate, LocalDate> {
    @Override
    public boolean isValid(LocalDate incidentDate, ConstraintValidatorContext context) {
        return incidentDate == null || !incidentDate.isAfter(LocalDate.now());
    }
}
