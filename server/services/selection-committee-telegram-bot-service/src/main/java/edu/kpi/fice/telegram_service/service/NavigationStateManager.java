package edu.kpi.fice.telegram_service.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Per-chat breadcrumb navigation stack for tracking screen history. */
@Service
public class NavigationStateManager {

  private static final int MAX_STACK_SIZE = 10;

  private final ConcurrentHashMap<Long, Deque<String>> chatStacks = new ConcurrentHashMap<>();

  /** Pushes the current screen's callback data onto the stack. */
  public void push(Long chatId, String callbackData) {
    Deque<String> stack = chatStacks.computeIfAbsent(chatId, k -> new ArrayDeque<>());
    // Avoid pushing duplicates when navigating to the same screen
    if (!stack.isEmpty() && stack.peekLast().equals(callbackData)) {
      return;
    }
    stack.addLast(callbackData);
    // Trim to max size
    while (stack.size() > MAX_STACK_SIZE) {
      stack.pollFirst();
    }
  }

  /** Pops and returns the previous screen's callback data (for Back navigation). */
  public Optional<String> pop(Long chatId) {
    Deque<String> stack = chatStacks.get(chatId);
    if (stack == null || stack.isEmpty()) {
      return Optional.empty();
    }
    // Pop current screen first
    stack.pollLast();
    // Return previous screen
    if (stack.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(stack.peekLast());
  }

  /** Clears the navigation stack (for Home navigation). */
  public void reset(Long chatId) {
    chatStacks.remove(chatId);
  }
}
