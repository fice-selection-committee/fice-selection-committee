package edu.kpi.fice.telegram_service.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "bot_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotUser {

  @Id
  @Column(name = "telegram_user_id")
  private Long telegramUserId;

  @Column(name = "chat_id", nullable = false)
  private Long chatId;

  @Column(name = "language_code", nullable = false, length = 5)
  @Builder.Default
  private String languageCode = "en";

  @Column(name = "username", length = 100)
  private String username;

  @Column(name = "role", nullable = false, length = 20)
  @Builder.Default
  private String role = "viewer";

  @Column(name = "subscribed", nullable = false)
  @Builder.Default
  private Boolean subscribed = false;

  @Column(name = "message_thread_id")
  private Integer messageThreadId;

  @Column(name = "created_at", nullable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Column(name = "last_active_at", nullable = false)
  @Builder.Default
  private Instant lastActiveAt = Instant.now();

  public void touch() {
    this.lastActiveAt = Instant.now();
  }
}
