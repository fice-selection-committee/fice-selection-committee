package edu.kpi.fice.sc.events.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TemplateType {
  VERIFY_EMAIL("verify_email"),
  PASSWORD_RESET("password_reset"),
  WELCOME_MESSAGE("welcome_message"),
  MAGIC_LINK("magic_link"),

  APPLICATION_SUBMITTED("application_submitted"),
  APPLICATION_STATUS_CHANGED("application_status_changed"),
  APPLICATION_APPROVED("application_approved"),

  DOCUMENT_RECEIVED("document_received"),
  DOCUMENT_REJECTED("document_rejected"),

  SYSTEM_MAINTENANCE("system_maintenance");

  private final String templateName;

  public String getSubjectKey() {
    return templateName + ".subject";
  }
}
