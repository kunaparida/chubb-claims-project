package com.claims.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class ClaimAmountValidator implements ConstraintValidator<ValidClaimAmount, BigDecimal> {
    @Override
    public boolean isValid(BigDecimal amount, ConstraintValidatorContext context) {
        if (amount == null) return true;
        if (amount.signum() <= 0 || amount.precision() - amount.scale() > 12) return false;
        try {
            amount.setScale(2, RoundingMode.UNNECESSARY);
            return true;
        } catch (ArithmeticException ex) {
            return false;
        }
    }
}
