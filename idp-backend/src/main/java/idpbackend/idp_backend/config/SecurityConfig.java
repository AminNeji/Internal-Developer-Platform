package idpbackend.idp_backend.config;

import idpbackend.idp_backend.service.AllowedUsersService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AllowedUsersService allowedUsersService) throws Exception {
        http
                // the ALB health check has no login, everything else does
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())

                // both endpoints live under /api so the existing ingress rule and dev proxy already reach the backend
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(endpoint -> endpoint.baseUri("/api/oauth2/authorization"))
                        .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/login/oauth2/code/*"))
                        .userInfoEndpoint(userInfo -> userInfo.userService(allowedUsersService))
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/?denied"))

                // without this Spring answers API calls with a redirect to a login page; Angular wants a plain 401
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

                // XSRF-TOKEN cookie that Angular's HttpClient sends back as a header on POST/DELETE
                .csrf(csrf -> csrf.spa())

                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()));

        return http.build();
    }
}
