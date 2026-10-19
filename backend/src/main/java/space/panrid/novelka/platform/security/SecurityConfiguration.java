package space.panrid.novelka.platform.security;

import java.io.IOException;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Web security. Every request passes the filter chain; who may do what is decided in
 * the services through {@code AccessPolicy}, with the account's current role read from
 * the database, so a demotion applies to already open sessions at once. A request with
 * {@code Authorization: Bearer} is signed in by its access token instead ({@link BearerFilter}).
 */
@Configuration
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
            JsonMapper json, ObjectProvider<BearerTokens> tokens) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                // The SPA reads XSRF-TOKEN from the cookie and sends it back in X-XSRF-TOKEN.
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).spa()
                        // Telegram's webhook carries its own secret header instead; browser errors carry no authority.
                        .ignoringRequestMatchers("/api/telegram/webhook", "/api/client-errors")
                        // A token is sent by the program itself, never added by a browser: no CSRF to forge.
                        .ignoringRequestMatchers((RequestMatcher) BearerFilter::carriesBearer))
                .addFilterAfter(new BearerFilter(tokens.getIfAvailable(() -> REFUSE_TOKENS), json), CsrfFilter.class)
                .securityContext(context -> context.securityContextRepository(contexts))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                problem(response, json, HttpStatus.UNAUTHORIZED, "Увійдіть, щоб продовжити."))
                        .accessDeniedHandler((request, response, error) -> problem(response, json,
                                HttpStatus.FORBIDDEN, error instanceof CsrfException
                                        ? "Сторінка застаріла. Оновіть її й спробуйте ще раз."
                                        : "Це вам недоступно.")))
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; "
                                        + "font-src 'self'; connect-src 'self'; frame-ancestors 'none'; "
                                        + "base-uri 'self'; form-action 'self'")))
                .build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Without the tokens' module every token is refused rather than let through. */
    private static final BearerTokens REFUSE_TOKENS = new BearerTokens() {
        @Override
        public Optional<TokenGrant> authenticate(String token, String address) {
            return Optional.empty();
        }

        @Override
        public String refusal(TokenGrant grant, String method, String path) {
            return "Токени не приймаються.";
        }

        @Override
        public void completed(TokenGrant grant, String method, String path, String address, int status) {
        }
    };

    static void problem(HttpServletResponse response, JsonMapper json, HttpStatus status, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detail));
    }
}
