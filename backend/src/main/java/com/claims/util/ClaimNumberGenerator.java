package com.claims.util;

import java.util.UUID;

public final class ClaimNumberGenerator {
    private ClaimNumberGenerator() {}

    public static String generate() {
        return "CLM-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
