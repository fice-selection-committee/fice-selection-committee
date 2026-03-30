package edu.kpi.fice.telegram_service.keyboard;

import static org.assertj.core.api.Assertions.assertThat;

import edu.kpi.fice.telegram_service.dto.BotResponse.InlineButton;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InlineKeyboardBuilder")
class InlineKeyboardBuilderTest {

  @Nested
  @DisplayName("basic operations")
  class BasicOperations {

    @Test
    @DisplayName("single button in one row")
    void singleButton() {
      List<List<InlineButton>> keyboard =
          InlineKeyboardBuilder.create().button("Click", "action:click").build();

      assertThat(keyboard).hasSize(1);
      assertThat(keyboard.get(0)).hasSize(1);
      assertThat(keyboard.get(0).get(0).text()).isEqualTo("Click");
      assertThat(keyboard.get(0).get(0).callbackData()).isEqualTo("action:click");
    }

    @Test
    @DisplayName("two buttons in one row")
    void twoButtonsOneRow() {
      List<List<InlineButton>> keyboard =
          InlineKeyboardBuilder.create().button("A", "data:a").button("B", "data:b").build();

      assertThat(keyboard).hasSize(1);
      assertThat(keyboard.get(0)).hasSize(2);
    }

    @Test
    @DisplayName("buttons in separate rows")
    void separateRows() {
      List<List<InlineButton>> keyboard =
          InlineKeyboardBuilder.create()
              .button("Row1", "data:1")
              .row()
              .button("Row2", "data:2")
              .build();

      assertThat(keyboard).hasSize(2);
      assertThat(keyboard.get(0)).hasSize(1);
      assertThat(keyboard.get(1)).hasSize(1);
    }

    @Test
    @DisplayName("empty builder returns empty list")
    void emptyBuilder() {
      List<List<InlineButton>> keyboard = InlineKeyboardBuilder.create().build();

      assertThat(keyboard).isEmpty();
    }
  }

  @Nested
  @DisplayName("navRow")
  class NavRow {

    @Test
    @DisplayName("navRow creates a row with back and home buttons")
    void navRowCreatesBackAndHome() {
      List<List<InlineButton>> keyboard =
          InlineKeyboardBuilder.create()
              .button("Content", "data:content")
              .row()
              .navRow("Back", "nav:back", "Home", "nav:home")
              .build();

      assertThat(keyboard).hasSize(2);
      List<InlineButton> navRow = keyboard.get(1);
      assertThat(navRow).hasSize(2);
      assertThat(navRow.get(0).text()).isEqualTo("Back");
      assertThat(navRow.get(0).callbackData()).isEqualTo("nav:back");
      assertThat(navRow.get(1).text()).isEqualTo("Home");
      assertThat(navRow.get(1).callbackData()).isEqualTo("nav:home");
    }
  }

  @Nested
  @DisplayName("build immutability")
  class BuildImmutability {

    @Test
    @DisplayName("build returns an immutable list")
    void buildReturnsImmutableList() {
      List<List<InlineButton>> keyboard = InlineKeyboardBuilder.create().button("A", "a").build();

      assertThat(keyboard).isUnmodifiable();
    }
  }
}
