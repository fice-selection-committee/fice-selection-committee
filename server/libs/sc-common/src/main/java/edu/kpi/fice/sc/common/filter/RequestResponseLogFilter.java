package edu.kpi.fice.sc.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class RequestResponseLogFilter extends OncePerRequestFilter {

  private static final String ID_HEADER = "Request-Id";
  private static final String LOG_FIELD_PATTERN = "%s:%s";
  private static final String ACTUATOR_MARKER = "actuator";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (isIgnoreLogging(request.getRequestURI())) {
      filterChain.doFilter(request, response);
      return;
    }

    final UUID requestId = UUID.randomUUID();
    fillRequestInHeader(response, requestId);
    logRequest(request, requestId);

    filterChain.doFilter(request, response);

    logResponse(response, requestId);
  }

  private void fillRequestInHeader(HttpServletResponse response, UUID requestId) {
    response.addHeader(ID_HEADER, requestId.toString());
  }

  private boolean isIgnoreLogging(String url) {
    return Objects.nonNull(url) && url.contains(ACTUATOR_MARKER);
  }

  private void logRequest(HttpServletRequest request, UUID requestId) {
    log.info(
        """
                        \n--------------- START Request {}
                        method - {}
                        requestURL - {}
                        queryString - {}
                        ip - {}
                        headers - {}
                        cookies - {}""",
        requestId,
        request.getMethod(),
        request.getRequestURL(),
        request.getQueryString() == null ? "" : request.getQueryString(),
        request.getRemoteAddr(),
        getRequestHeaders(request),
        getRequestCookies(request));
  }

  private String getRequestHeaders(HttpServletRequest request) {
    return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(
                request.getHeaderNames().asIterator(), Spliterator.ORDERED),
            false)
        .map(
            key -> String.format(LOG_FIELD_PATTERN, key, formatHeader(key, request.getHeader(key))))
        .collect(Collectors.joining(", "));
  }

  private String getResponseHeaders(HttpServletResponse response) {
    return response.getHeaderNames().stream()
        .map(
            header ->
                String.format(
                    LOG_FIELD_PATTERN, header, formatHeader(header, response.getHeader(header))))
        .collect(Collectors.joining(", "));
  }

  private String getRequestCookies(HttpServletRequest request) {
    return Optional.ofNullable(request.getCookies()).stream()
        .flatMap(Arrays::stream)
        .map(cookie -> String.format(LOG_FIELD_PATTERN, cookie.getName(), "[REDACTED]"))
        .collect(Collectors.joining(", "));
  }

  private void logResponse(HttpServletResponse response, UUID requestId) {
    log.info(
        """
                        status - {}
                        headers - {}
                        --------------- FINISH Request {}""",
        response.getStatus(),
        getResponseHeaders(response),
        requestId);
  }

  private String formatHeader(String headerName, String headerValue) {
    Set<String> sensitiveHeaders = Set.of("authorization", "cookie", "x-internal-security-token");
    if (sensitiveHeaders.contains(headerName.toLowerCase())) {
      return "[REDACTED]";
    }
    return headerValue;
  }
}
