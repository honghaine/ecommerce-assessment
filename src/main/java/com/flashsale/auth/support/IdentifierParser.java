package com.flashsale.auth.support;

import java.util.Locale;
import java.util.regex.Pattern;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.springframework.stereotype.Component;

import com.flashsale.auth.model.Identifier;
import com.flashsale.auth.model.IdentifierType;
import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;

/**
 * Decides email vs phone from a single {@code identifier} input and normalizes it:
 * emails are lower-cased, phones must be international ({@code +84...}) and are stored as E.164.
 */
@Component
public class IdentifierParser {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$");
    private static final Pattern PHONE_CHARS = Pattern.compile("^\\+[0-9 ().-]{6,24}$");

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    public Identifier parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid();
        }
        String input = raw.trim();
        return input.contains("@") ? parseEmail(input) : parsePhone(input);
    }

    private Identifier parseEmail(String input) {
        String email = input.toLowerCase(Locale.ROOT);
        if (email.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(email).matches()) {
            throw invalid();
        }
        return new Identifier(IdentifierType.EMAIL, email);
    }

    private Identifier parsePhone(String input) {
        if (!PHONE_CHARS.matcher(input).matches()) {
            throw invalid();
        }
        try {
            Phonenumber.PhoneNumber number = phoneNumberUtil.parse(input, null);
            if (!phoneNumberUtil.isValidNumber(number)) {
                throw invalid();
            }
            return new Identifier(IdentifierType.PHONE,
                    phoneNumberUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164));
        } catch (NumberParseException ex) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.INVALID_IDENTIFIER);
    }
}
