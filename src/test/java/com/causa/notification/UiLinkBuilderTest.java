package com.causa.notification;

import com.causa.core.services.ConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("UiLinkBuilder Tests")
class UiLinkBuilderTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final UiLinkBuilder builder = new UiLinkBuilder(configService);

    private void baseUrlIs(String value) {
        when(configService.get("UI_BASE_URL")).thenReturn(Optional.ofNullable(value));
    }

    @Test
    @DisplayName("Builds the diagnosis link from the base address")
    void buildsLink() {
        baseUrlIs("https://ui.example.com");
        assertEquals(Optional.of("https://ui.example.com/active-issues/abc-123"), builder.diagnosisLink("abc-123"));
    }

    @Test
    @DisplayName("Base address with and without trailing slash gives the same link")
    void trailingSlashIgnored() {
        baseUrlIs("https://ui.example.com");
        Optional<String> without = builder.diagnosisLink("abc");
        baseUrlIs("https://ui.example.com/");
        assertEquals(without, builder.diagnosisLink("abc"));
        baseUrlIs("https://ui.example.com//");
        assertEquals(without, builder.diagnosisLink("abc"));
    }

    @Test
    @DisplayName("Empty, blank or missing base address gives no link")
    void noBaseAddress() {
        baseUrlIs("");
        assertTrue(builder.diagnosisLink("abc").isEmpty());
        baseUrlIs("   ");
        assertTrue(builder.diagnosisLink("abc").isEmpty());
        baseUrlIs(null);
        assertTrue(builder.diagnosisLink("abc").isEmpty());
    }

    @Test
    @DisplayName("Empty, blank or null id gives no link")
    void noId() {
        baseUrlIs("https://ui.example.com");
        assertTrue(builder.diagnosisLink("").isEmpty());
        assertTrue(builder.diagnosisLink("  ").isEmpty());
        assertTrue(builder.diagnosisLink(null).isEmpty());
    }

    @Test
    @DisplayName("A changed base address is used on the next call, without recreating the builder")
    void picksUpChangedValue() {
        baseUrlIs("https://old.example.com");
        assertEquals(Optional.of("https://old.example.com/active-issues/abc"), builder.diagnosisLink("abc"));
        baseUrlIs("https://new.example.com");
        assertEquals(Optional.of("https://new.example.com/active-issues/abc"), builder.diagnosisLink("abc"));
    }
}
