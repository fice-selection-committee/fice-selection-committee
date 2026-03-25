package edu.kpi.fice.sc.events.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChannelType {
  EMAIL("mail"),
  TELEGRAM("telegram");

  private final String type;
}
