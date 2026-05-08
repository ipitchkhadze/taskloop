package com.example.tasklist.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AdviceRateLimitFilter extends OncePerRequestFilter {

    private final AdviceRateLimitProperties props;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private static final String ADVICE_PATTERN = "/api/v1/tasks/*/advice";
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public AdviceRateLimitFilter(AdviceRateLimitProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!props.isEnabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        if (!StringUtils.hasText(uri)) {
            return true;
        }
        String path = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;
        return !pathMatcher.match(ADVICE_PATTERN, path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = clientKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());
        if (!bucket.tryConsume(1)) {
            writeTooManyRequests(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Bucket newBucket() {
        int capacity = Math.max(1, props.getRequestsPerWindow());
        long windowSeconds = Math.max(1L, props.getWindowSeconds());
        Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(capacity, Duration.ofSeconds(windowSeconds)));
        return Bucket.builder().addLimit(limit).build();
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String addr = request.getRemoteAddr();
        return StringUtils.hasText(addr) ? addr : "unknown";
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("path", request.getRequestURI());
        body.put("detail", "Слишком много запросов советов. Подождите немного.");

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
