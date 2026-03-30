package edu.kpi.fice.telegram_service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import edu.kpi.fice.telegram_service.dto.FeatureFlagResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResilientEnvironmentServiceClient")
class ResilientEnvironmentServiceClientTest {

  @Mock private EnvironmentServiceClient delegate;

  private ResilientEnvironmentServiceClient client;

  @BeforeEach
  void setUp() {
    client = new ResilientEnvironmentServiceClient(delegate);
  }

  @Nested
  @DisplayName("getFlags")
  class GetFlags {

    @Test
    @DisplayName("delegates to underlying client on success")
    void getFlags_delegates() {
      List<FeatureFlagResponse> flags =
          List.of(new FeatureFlagResponse("f1", true, null, null, null, null, null, null));
      when(delegate.getFlags(null, null)).thenReturn(flags);

      assertThat(client.getFlags(null, null)).isEqualTo(flags);
    }

    @Test
    @DisplayName("fallback returns cached flags when delegate fails")
    void getFlags_fallbackReturnsCached() {
      // First call succeeds and caches
      List<FeatureFlagResponse> flags =
          List.of(new FeatureFlagResponse("f1", true, null, null, null, null, null, null));
      when(delegate.getFlags(null, null)).thenReturn(flags);
      client.getFlags(null, null);

      // Fallback should return cached flags
      List<FeatureFlagResponse> result =
          client.getFlagsFallback(null, null, new RuntimeException("down"));
      assertThat(result).hasSize(1);
      assertThat(result.get(0).key()).isEqualTo("f1");
    }
  }

  @Nested
  @DisplayName("getFlagByKey")
  class GetFlagByKey {

    @Test
    @DisplayName("delegates to underlying client on success")
    void getFlagByKey_delegates() {
      FeatureFlagResponse flag =
          new FeatureFlagResponse("f1", true, null, null, null, null, null, null);
      when(delegate.getFlagByKey("f1")).thenReturn(flag);

      assertThat(client.getFlagByKey("f1")).isEqualTo(flag);
    }

    @Test
    @DisplayName("fallback returns cached flag if available")
    void getFlagByKey_fallbackReturnsCachedFlag() {
      // Populate cache
      List<FeatureFlagResponse> flags =
          List.of(new FeatureFlagResponse("f1", true, null, null, null, null, null, null));
      when(delegate.getFlags(null, null)).thenReturn(flags);
      client.getFlags(null, null);

      FeatureFlagResponse result = client.getFlagByKeyFallback("f1", new RuntimeException("down"));
      assertThat(result.key()).isEqualTo("f1");
    }

    @Test
    @DisplayName("fallback throws when flag is not in cache")
    void getFlagByKey_fallbackThrowsWhenNotInCache() {
      assertThatThrownBy(() -> client.getFlagByKeyFallback("missing", new RuntimeException("down")))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("not in cache");
    }
  }

  @Nested
  @DisplayName("toggleFlag")
  class ToggleFlag {

    @Test
    @DisplayName("fallback always throws for write operations")
    void toggleFlag_fallbackAlwaysThrows() {
      assertThatThrownBy(
              () -> client.toggleFlagFallback("f1", true, "actor", new RuntimeException("down")))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("unavailable");
    }
  }
}
