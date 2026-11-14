package com.fraudguard.api.service;

import com.fraudguard.exception.InvalidRequestParameterException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionCursorCodecTest {

    private final DecisionCursorCodec codec = new DecisionCursorCodec();

    @Test
    void shouldEncodeAndDecodePositiveId() {
        String cursor = codec.encode(123L);

        assertThat(cursor).isNotBlank();
        assertThat(codec.decode(cursor)).isEqualTo(123L);
    }

    @Test
    void shouldReturnNullForNullCursor() {
        assertThat(codec.decode(null)).isNull();
    }

    @Test
    void shouldRejectMalformedBase64() {
        assertThatThrownBy(() -> codec.decode("not-valid%%%"))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void shouldRejectUnsupportedVersion() {
        String cursor = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("v2:123".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(cursor))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void shouldRejectZeroAndNegativeIds() {
        for (String value : new String[]{"v1:0", "v1:-1"}) {
            String cursor = java.util.Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            assertThatThrownBy(() -> codec.decode(cursor))
                    .isInstanceOf(InvalidRequestParameterException.class);
        }
    }

    @Test
    void shouldRejectLongOverflow() {
        String cursor = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("v1:999999999999999999999999999".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(cursor))
                .isInstanceOf(InvalidRequestParameterException.class);
    }
}