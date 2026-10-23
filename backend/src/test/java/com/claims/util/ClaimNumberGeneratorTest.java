package com.claims.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ClaimNumberGeneratorTest {
    private static final Pattern CLAIM_NUMBER = Pattern.compile("CLM-[0-9A-F]{8}-[0-9A-F]{3}");

    @Test
    void generatesUniqueClaimNumbersInTheExpectedFormat() {
        Set<String> generated = new HashSet<>();

        for (int index = 0; index < 100; index++) {
            String claimNumber = ClaimNumberGenerator.generate();
            assertTrue(CLAIM_NUMBER.matcher(claimNumber).matches(), claimNumber);
            generated.add(claimNumber);
        }

        assertEquals(100, generated.size());
    }
}
