package com.claims.notification.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MaskingTest {

    @Test
    void keepsOnlyTheFirstLetterAndTheDomain() {
        assertEquals("j***@example.com", Masking.email("jane.doe@example.com"));
    }

    @Test
    void neverFailsOnOddInput() {
        assertEquals("(none)", Masking.email(null));
        assertEquals("(none)", Masking.email("  "));
        assertEquals("***", Masking.email("no-at-sign"));
        assertEquals("***", Masking.email("@example.com"));
    }
}
