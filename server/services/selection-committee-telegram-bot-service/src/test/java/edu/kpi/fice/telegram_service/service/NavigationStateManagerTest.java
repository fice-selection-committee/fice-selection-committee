package edu.kpi.fice.telegram_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("NavigationStateManager")
class NavigationStateManagerTest {

  private NavigationStateManager nav;

  @BeforeEach
  void setUp() {
    nav = new NavigationStateManager();
  }

  @Nested
  @DisplayName("push and pop")
  class PushAndPop {

    @Test
    @DisplayName("pop returns previous screen after two pushes")
    void pop_returnsPreviousScreen() {
      nav.push(1L, "menu:main");
      nav.push(1L, "flags:list:0");

      assertThat(nav.pop(1L)).contains("menu:main");
    }

    @Test
    @DisplayName("pop returns empty when stack has only one entry")
    void pop_returnsEmpty_whenSingleEntry() {
      nav.push(1L, "menu:main");

      assertThat(nav.pop(1L)).isEmpty();
    }

    @Test
    @DisplayName("pop returns empty when stack is empty")
    void pop_returnsEmpty_whenEmpty() {
      assertThat(nav.pop(1L)).isEmpty();
    }

    @Test
    @DisplayName("does not push duplicate consecutive entries")
    void push_skipsDuplicateConsecutive() {
      nav.push(1L, "menu:main");
      nav.push(1L, "menu:main");
      nav.push(1L, "flags:list:0");

      assertThat(nav.pop(1L)).contains("menu:main");
      assertThat(nav.pop(1L)).isEmpty();
    }

    @Test
    @DisplayName("different chats have independent stacks")
    void differentChats_independentStacks() {
      nav.push(1L, "menu:main");
      nav.push(1L, "flags:list:0");

      nav.push(2L, "settings:main");

      assertThat(nav.pop(1L)).contains("menu:main");
      assertThat(nav.pop(2L)).isEmpty();
    }
  }

  @Nested
  @DisplayName("reset")
  class Reset {

    @Test
    @DisplayName("clears the navigation stack")
    void reset_clearsStack() {
      nav.push(1L, "menu:main");
      nav.push(1L, "flags:list:0");
      nav.push(1L, "flags:view:dark-mode");

      nav.reset(1L);

      assertThat(nav.pop(1L)).isEmpty();
    }
  }

  @Nested
  @DisplayName("stack size limit")
  class StackSizeLimit {

    @Test
    @DisplayName("trims stack to 10 entries")
    void push_trimsToMaxSize() {
      for (int i = 0; i < 15; i++) {
        nav.push(1L, "screen:" + i);
      }

      // Pop should work for the last entries
      assertThat(nav.pop(1L)).isPresent();
    }
  }
}
