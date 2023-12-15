package io.flowcore.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Servlet filter that extracts the Bearer JWT from the {@code Authorization} header,
 * validates it, and populates the {@link SecurityContext} with a
 * {@link FlowcoreAuthentication}.
 * <p>
 * Requests to actuator endpoints (configurable via {@code flowcore.security.public-endpoints})
 * are skipped automatically.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenValidator tokenValidator;
    private final Set<String> publicEndpoints;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final SecurityContextHolderStrategy contextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    private AuthenticationFailureHandler failureHandler = (request, response, exception) ->
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, exception.getMessage());

    /**
     * Constructs a new {@code JwtAuthenticationFilter}.
     *
     * @param tokenValidator  the JWT token validator
     * @param publicEndpoints endpoint patterns that should bypass JWT validation
     */
    public JwtAuthenticationFilter(JwtTokenValidator tokenValidator,
                                   Set<String> publicEndpoints) {
        this.tokenValidator = tokenValidator;
        this.publicEndpoints = publicEndpoints != null ? Set.copyOf(publicEndpoints) : Set.of();
    }

    /**
     * Sets a custom authentication failure handler.
     *
     * @param failureHandler the failure handler
     */
    public void setFailureHandler(AuthenticationFailureHandler failureHandler) {
        this.failureHandler = failureHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String pattern : publicEndpoints) {
            if (pathMatcher.match(pattern, path)) {
                log.trace("Skipping JWT authentication for public endpoint: {}", path);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.trace("No Bearer token found in Authorization header for: {}",
                    request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            JwtClaims claims = tokenValidator.validate(token);

            FlowcoreAuthentication authentication = new FlowcoreAuthentication(
                    claims.subject(),
                    claims.roles(),
                    claims.tenant(),
                    claims.scopes(),
                    claims
            );

            SecurityContext context = contextHolderStrategy.createEmptyContext();
            context.setAuthentication(authentication);
            contextHolderStrategy.setContext(context);

            log.debug("Authenticated subject={} tenant={} roles={}",
                    claims.subject(), claims.tenant(), claims.roles());

            filterChain.doFilter(request, response);
        } catch (JwtValidationException ex) {
            log.debug("JWT validation failed for {}: {}", request.getRequestURI(),
                    ex.getMessage());
            contextHolderStrategy.clearContext();
            failureHandler.onAuthenticationFailure(request, response,
                    new BadCredentialsException(ex.getMessage(), ex));
        }
    }
}
