package edu.kpi.fice.sc.events.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChannelType {
  EMAIL("mail"),
  TELEGRAM("telegram"),
  IN_APP("in_app");

  private final String type;
}
