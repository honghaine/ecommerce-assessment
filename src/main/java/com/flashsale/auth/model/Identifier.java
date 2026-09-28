package com.flashsale.auth.model;

import com.flashsale.common.logging.PiiMasker;

/**
 * Normalized login identifier: lower-cased email or E.164 phone number.
 */
public record Identifier(IdentifierType type, String value) {

    public boolean isEmail() {
        return type == IdentifierType.EMAIL;
    }

    @Override
    public String toString() {
        return type + ":" + PiiMasker.mask(value);
    }
}
