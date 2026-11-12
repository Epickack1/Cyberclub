package ru.mirea.cyberclub.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import ru.mirea.cyberclub.backend.domain.BookingStatus;
import ru.mirea.cyberclub.backend.domain.RiskLevel;
import ru.mirea.cyberclub.backend.error.BadRequestException;

/** Вычисляемый показатель (риск неоплаты) и правила тегов — без Spring и базы. */
class RiskLevelAndTagsTest {

    @Test
    void paidOrCancelledBookingHasNoRisk() {
        assertThat(RiskLevel.of(BookingStatus.CONFIRMED, true, new BigDecimal("5000"))).isEqualTo(RiskLevel.NONE);
        assertThat(RiskLevel.of(BookingStatus.CANCELLED, false, new BigDecimal("5000"))).isEqualTo(RiskLevel.NONE);
    }

    @Test
    void unpaidRiskGrowsWithPriceAtThresholds() {
        assertThat(RiskLevel.of(BookingStatus.CREATED, false, new BigDecimal("799.99"))).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.of(BookingStatus.CREATED, false, new BigDecimal("800"))).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskLevel.of(BookingStatus.CONFIRMED, false, new BigDecimal("1999.99"))).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskLevel.of(BookingStatus.CONFIRMED, false, new BigDecimal("2000"))).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void unpaidGameInProgressOrFinishedIsHighRiskEvenWhenCheap() {
        assertThat(RiskLevel.of(BookingStatus.ACTIVE, false, new BigDecimal("150"))).isEqualTo(RiskLevel.HIGH);
        assertThat(RiskLevel.of(BookingStatus.COMPLETED, false, new BigDecimal("150"))).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void tagsAreNormalizedAndValidated() {
        assertThat(Tags.normalizeAll(List.of("  Турнир ", "турнир", "VIP", "night_owl"), "tags"))
                .containsExactly("night_owl", "vip", "турнир");
        assertThatThrownBy(() -> Tags.normalize("с пробелом", "tags")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Tags.normalize("x", "tags")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Tags.normalizeAll(List.of("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "b1", "b2"), "tags"))
                .isInstanceOf(BadRequestException.class);
    }
}
