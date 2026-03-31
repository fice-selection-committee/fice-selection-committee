package edu.kpi.fice.sc.events.dto;

import edu.kpi.fice.sc.events.enums.ChannelType;
import edu.kpi.fice.sc.events.enums.TemplateType;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder
public record NotificationEventDto(
    TemplateType templateType,
    String userEmail,
    Long telegramId,
    Long userId,
    Map<String, Object> payload,
    List<ChannelType> channels,
    String language) {}
