package com.example.pharmacy.direction.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62ServiceTest {

    private final Base62Service base62Service = new Base62Service();

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 61, 62, 125, 99_999_999L})
    void 인코딩한_값을_디코딩하면_원래_id가_나온다(long id) {
        String encoded = base62Service.encode(id);
        assertThat(base62Service.decode(encoded)).isEqualTo(id);
    }

    @Test
    void 숫자_62는_10으로_인코딩된다() {
        assertThat(base62Service.encode(62)).isEqualTo("10");
    }

    @Test
    void 허용되지_않은_문자가_있으면_예외() {
        assertThatThrownBy(() -> base62Service.decode("ab-c"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}