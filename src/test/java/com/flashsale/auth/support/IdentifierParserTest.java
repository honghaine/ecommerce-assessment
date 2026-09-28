package com.flashsale.auth.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.flashsale.auth.model.Identifier;
import com.flashsale.auth.model.IdentifierType;
import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;

class IdentifierParserTest {

    private final IdentifierParser parser = new IdentifierParser();

    @Test
    void emailIsTrimmedAndLowerCased() {
        Identifier id = parser.parse("  John.Doe+sale@Example.COM ");

        assertThat(id.type()).isEqualTo(IdentifierType.EMAIL);
        assertThat(id.value()).isEqualTo("john.doe+sale@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"+84912345678", "+84 912 345 678", "+84-912-345-678", "+84 (91) 234-5678"})
    void internationalPhoneIsNormalizedToE164(String raw) {
        Identifier id = parser.parse(raw);

        assertThat(id.type()).isEqualTo(IdentifierType.PHONE);
        assertThat(id.value()).isEqualTo("+84912345678");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-an-email", "a@b", "@example.com", "0912345678", "+84123", "+abc",
            "+999999999999"})
    void invalidIdentifiersAreRejected(String raw) {
        assertThatThrownBy(() -> parser.parse(raw))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_IDENTIFIER);
    }

    @Test
    void toStringMasksValue() {
        assertThat(parser.parse("john.doe@example.com").toString()).isEqualTo("EMAIL:j******e@example.com");
    }
}
