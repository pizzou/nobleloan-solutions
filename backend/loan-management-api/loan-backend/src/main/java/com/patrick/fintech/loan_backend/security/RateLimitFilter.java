package com.patrick.fintech.loan_backend.security;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(
            String method,
            String path,
            int max,
            long window,
            String label
    ) {
    }

    private final DistributedRateLimitService limiter;

    private final List<Rule> rules = List.of(

            new Rule(
                    "POST",
                    "/api/auth/login",
                    20,
                    TimeUnit.MINUTES.toMillis(5),
                    "login"
            ),

            new Rule(
                    "POST",
                    "/api/auth/register",
                    5,
                    TimeUnit.HOURS.toMillis(1),
                    "register"
            ),

            new Rule(
                    "POST",
                    "/api/auth/forgot-password",
                    5,
                    TimeUnit.HOURS.toMillis(1),
                    "forgot"
            ),

            new Rule(
                    "POST",
                    "/api/auth/reset-password",
                    10,
                    TimeUnit.HOURS.toMillis(1),
                    "reset"
            ),

            new Rule(
                    "ANY",
                    "/api/public/",
                    60,
                    TimeUnit.MINUTES.toMillis(1),
                    "public"
            ),

            new Rule(
                    "POST",
                    "/api/public/webhooks/",
                    120,
                    TimeUnit.MINUTES.toMillis(1),
                    "webhook"
            )
    );

    /*
     * ============================================================
     * CORS PREFLIGHT
     * ============================================================
     *
     * OPTIONS requests are browser CORS preflight requests.
     * They must not consume rate-limit capacity.
     */
    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {

        return "OPTIONS".equalsIgnoreCase(
                request.getMethod()
        );
    }

    /*
     * ============================================================
     * RATE LIMIT REQUEST
     * ============================================================
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String path =
                request.getRequestURI();

        String method =
                request.getMethod();

        for (Rule rule : rules) {

            boolean methodMatches =
                    "ANY".equalsIgnoreCase(rule.method())
                            || rule.method()
                                    .equalsIgnoreCase(method);

            boolean pathMatches =
                    path.startsWith(
                            rule.path()
                    );

            /*
             * This rule does not apply to this request.
             */
            if (!methodMatches || !pathMatches) {
                continue;
            }

            String key =
                    rule.label()
                            + ":"
                            + clientIp(request);

            try {

                var decision =
                        limiter.check(
                                key,
                                rule.max(),
                                rule.window()
                        );

                /*
                 * ====================================================
                 * RATE LIMIT EXCEEDED
                 * ====================================================
                 *
                 * Do NOT use:
                 *
                 * HttpServletResponse.SC_TOO_MANY_REQUESTS
                 *
                 * because that constant is not available in the
                 * Servlet API version used by this project.
                 *
                 * HTTP 429 means "Too Many Requests".
                 */
                if (!decision.allowed()) {

                    response.setStatus(429);

                    response.setHeader(
                            "Retry-After",
                            String.valueOf(
                                    decision.retryAfterSeconds()
                            )
                    );

                    response.setContentType(
                            "application/json"
                    );

                    response.setCharacterEncoding(
                            "UTF-8"
                    );

                    response.getWriter().write(
                            """
                            {
                              "success": false,
                              "error": "Too many requests. Please try again later."
                            }
                            """
                    );

                    return;
                }

            } catch (Exception exception) {

                /*
                 * ====================================================
                 * FAIL OPEN
                 * ====================================================
                 *
                 * The rate limiter is a protection mechanism.
                 *
                 * If the limiter itself has a temporary problem,
                 * it must not make the entire application unavailable.
                 *
                 * Therefore the request continues normally.
                 */
                log.error(
                        "Rate limiter unavailable for {} {}",
                        method,
                        path,
                        exception
                );
            }

            /*
             * Only the first matching rule should be applied.
             */
            break;
        }

        /*
         * Continue with the remaining Spring Security filters and
         * ultimately the controller.
         */
        chain.doFilter(
                request,
                response
        );
    }

    /*
     * ============================================================
     * CLIENT IP
     * ============================================================
     */
    private String clientIp(
            HttpServletRequest request
    ) {

        String remoteAddress =
                request.getRemoteAddr();

        if (
                remoteAddress == null
                        || remoteAddress.isBlank()
        ) {
            return "unknown";
        }

        return remoteAddress.trim();
    }

    /*
     * ============================================================
     * CLEANUP
     * ============================================================
     *
     * Periodically remove old rate-limit entries.
     */
    @Scheduled(
            fixedRate = 1,
            timeUnit = TimeUnit.HOURS
    )
    public void cleanup() {

        try {

            limiter.cleanup(
                    TimeUnit.HOURS.toMillis(2)
            );

        } catch (Exception exception) {

            /*
             * Cleanup failure must not stop the application.
             */
            log.warn(
                    "Rate-limit cleanup failed",
                    exception
            );
        }
    }
}

