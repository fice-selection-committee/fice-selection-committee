package edu.kpi.fice.sc.events.constants;

/** Shared RabbitMQ exchange, queue, and routing key constants used across services. */
public final class EventConstants {

  private EventConstants() {}

  // ── Exchanges ─────────────────────────────────────────────
  public static final String ADMISSION_EXCHANGE = "admission.events";
  public static final String DOCUMENTS_EXCHANGE = "documents.events";
  public static final String AUDIT_EXCHANGE = "audit.events";
  public static final String IDENTITY_EXCHANGE = "identity.events";

  // ── Queues ────────────────────────────────────────────────
  public static final String ADMISSION_QUEUE = "admission.queue";
  public static final String DOCUMENTS_QUEUE = "documents.queue";
  public static final String AUDIT_QUEUE = "audit.queue";
  public static final String IDENTITY_QUEUE = "identity.queue";

  // ── Dead Letter ───────────────────────────────────────────
  public static final String NOTIFICATIONS_DLX = "notifications.dlx";
  public static final String NOTIFICATIONS_DLQ = "notifications.dlq";
  public static final String DLQ_ROUTING_KEY = "dlq";

  // ── Routing Keys ──────────────────────────────────────────
  public static final String ADMISSION_APPLICATION_ROUTING = "admission.application.#";
  public static final String AUDIT_ROUTING = "audit.#";
  public static final String AUDIT_INGEST_ROUTING = "audit.ingest";
  public static final String AUDIT_IDENTITY_OUTBOX_ROUTING = "audit.identity.outbox";
  public static final String IDENTITY_NOTIFICATION_ROUTING = "identity.notification.#";
  public static final String IDENTITY_NOTIFICATION_EMAIL_ROUTING = "identity.notification.email";
}
