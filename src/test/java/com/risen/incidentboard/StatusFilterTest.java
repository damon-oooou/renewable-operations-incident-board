package com.risen.incidentboard;

import com.risen.incidentboard.domain.AlertStatus;
import com.risen.incidentboard.web.StatusFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatusFilterTest {

    @Test
    void defaultsToOpenOnly() {
        assertThat(StatusFilter.parse(null)).isEqualTo(AlertStatus.OPEN);
        assertThat(StatusFilter.parse("")).isEqualTo(AlertStatus.OPEN);
        assertThat(StatusFilter.parse("open")).doesNotContain(
                AlertStatus.RESOLVED, AlertStatus.DISMISSED);
    }

    @Test
    void allReachesClosedAlerts() {
        // Hiding closed alerts is default filter state, not a hard exclusion.
        assertThat(StatusFilter.parse("all"))
                .contains(AlertStatus.RESOLVED, AlertStatus.DISMISSED);
    }

    @Test
    void namingAClosedStatusDirectlyAlsoReachesIt() {
        assertThat(StatusFilter.parse("resolved")).containsExactly(AlertStatus.RESOLVED);
    }

    @Test
    void rejectsAnUnknownStatus() {
        assertThatThrownBy(() -> StatusFilter.parse("pending"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
