package com.flashsale.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiMaskerTest {

    @Test
    void masksEmailLocalPart() {
        assertThat(PiiMasker.maskEmail("john.doe@gmail.com")).isEqualTo("j******e@gmail.com");
        assertThat(PiiMasker.maskEmail("ab@gmail.com")).isEqualTo("a*@gmail.com");
        assertThat(PiiMasker.maskEmail(null)).isNull();
    }

    @Test
    void masksPhoneMiddleDigits() {
        assertThat(PiiMasker.maskPhone("+84912345678")).isEqualTo("+84******678");
        assertThat(PiiMasker.maskPhone("12345")).isEqualTo("***");
    }

    @Test
    void maskPicksStrategyByShape() {
        assertThat(PiiMasker.mask("john.doe@gmail.com")).isEqualTo("j******e@gmail.com");
        assertThat(PiiMasker.mask("+84912345678")).isEqualTo("+84******678");
    }
}
