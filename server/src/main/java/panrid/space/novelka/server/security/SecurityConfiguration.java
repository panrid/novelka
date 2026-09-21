package panrid.space.novelka.server.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import panrid.space.novelka.server.account.AccountService;
import panrid.space.novelka.server.config.ReaderDatabase;
import panrid.space.novelka.server.repository.AccountRepository;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    UserDetailsService users(ReaderDatabase database) {
        return username -> {
            try (var jdbc = database.open()) {
                var account = new AccountRepository(jdbc).credentials(AccountService.username(username));
                if (account == null) throw new UsernameNotFoundException("Invalid credentials");
                return User.withUsername((String) account.get("username"))
                        .password((String) account.get("password_hash")).roles((String) account.get("role")).build();
            } catch (Exception error) {
                throw new UsernameNotFoundException("Invalid credentials");
            }
        };
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        // Spring's welcome page forwards GET / to /index.html, which is authorized again.
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/favicon.ico", "/api/novels/**",
                                "/api/auth/me", "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .formLogin(login -> login.loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) -> response.setStatus(204))
                        .failureHandler((request, response, error) -> response.setStatus(401)))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> response.setStatus(401))
                        .accessDeniedHandler((request, response, error) -> response.setStatus(403)))
                .addFilterBefore(new LoginRateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
