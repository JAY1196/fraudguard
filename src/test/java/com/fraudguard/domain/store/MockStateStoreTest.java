package com.fraudguard.domain.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockStateStoreTest {

    @Mock
    private StateStore stateStore;

    @Test
    @DisplayName("StateStore abstraction can be easily mocked in isolated unit tests with Mockito")
    void shouldAllowMockitoCollaboration() {
        when(stateStore.recordAndCountSlidingWindow(eq("fraudguard:velocity:w1"), anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(5L);
        when(stateStore.getRecentAmounts("fraudguard:amounts:w1"))
                .thenReturn(List.of(100000L, 200000L, 150000L));
        when(stateStore.isHealthy()).thenReturn(true);

        long count = stateStore.recordAndCountSlidingWindow("fraudguard:velocity:w1", "evt-1", 1000L, 60000L, 120L);
        List<Long> recent = stateStore.getRecentAmounts("fraudguard:amounts:w1");
        boolean healthy = stateStore.isHealthy();

        assertThat(count).isEqualTo(5L);
        assertThat(recent).containsExactly(100000L, 200000L, 150000L);
        assertThat(healthy).isTrue();

        stateStore.recordAmount("fraudguard:amounts:w1", "evt-1", 500000L, 1000L, 10, 86400L);
        verify(stateStore).recordAmount("fraudguard:amounts:w1", "evt-1", 500000L, 1000L, 10, 86400L);
    }
}
