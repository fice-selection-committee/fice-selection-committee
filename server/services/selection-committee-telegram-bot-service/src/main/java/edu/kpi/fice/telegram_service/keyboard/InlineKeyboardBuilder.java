package edu.kpi.fice.telegram_service.keyboard;

import edu.kpi.fice.telegram_service.dto.BotResponse.InlineButton;
import java.util.ArrayList;
import java.util.List;

/** Fluent builder for constructing inline keyboard layouts for Telegram bot responses. */
public final class InlineKeyboardBuilder {

  private final List<List<InlineButton>> rows = new ArrayList<>();
  private List<InlineButton> currentRow = new ArrayList<>();

  private InlineKeyboardBuilder() {}

  public static InlineKeyboardBuilder create() {
    return new InlineKeyboardBuilder();
  }

  public InlineKeyboardBuilder button(String text, String callbackData) {
    currentRow.add(new InlineButton(text, callbackData));
    return this;
  }

  public InlineKeyboardBuilder row() {
    if (!currentRow.isEmpty()) {
      rows.add(currentRow);
      currentRow = new ArrayList<>();
    }
    return this;
  }

  public InlineKeyboardBuilder navRow(
      String backText, String backData, String homeText, String homeData) {
    row();
    button(backText, backData);
    button(homeText, homeData);
    return row();
  }

  public List<List<InlineButton>> build() {
    if (!currentRow.isEmpty()) {
      rows.add(currentRow);
    }
    return List.copyOf(rows);
  }
}
