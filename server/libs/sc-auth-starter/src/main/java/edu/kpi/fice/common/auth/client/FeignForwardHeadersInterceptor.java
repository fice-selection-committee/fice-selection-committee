package edu.kpi.fice.common.auth.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class FeignForwardHeadersInterceptor implements RequestInterceptor {
  @Override
  public void apply(RequestTemplate template) {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      HttpServletRequest request = attributes.getRequest();
      String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
      if (authorization != null) {
        template.header("Authorization", authorization);
      }
      String cookie = request.getHeader("Cookie");
      if (cookie != null) {
        template.header("Cookie", cookie);
      }
    }
  }
}
