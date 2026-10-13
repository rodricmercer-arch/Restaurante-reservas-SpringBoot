package com.restaurante.reservas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // activa @PreAuthorize en los servicios (segunda barrera, además de las rutas)
public class SecurityConfig {

    private final AppUserDetailsService userDetailsService;

    public SecurityConfig(AppUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authProvider())
            .authorizeHttpRequests(auth -> auth
                // El orden importa: Spring Security aplica la PRIMERA regla que coincide.
                .requestMatchers("/", "/login", "/css/**", "/js/**", "/img/**", "/error", "/403").permitAll()
                .requestMatchers("/reportes/**").hasRole("ADMIN")
                .requestMatchers("/perfil/**").hasAnyRole("CLIENTE", "ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/cliente/**").hasRole("CLIENTE")
                .requestMatchers("/api/**").authenticated() // PROVISIONAL (Fase 3): la Fase 4 definirá los permisos de la API
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/")
                .loginProcessingUrl("/login")
                .usernameParameter("dni")
                .passwordParameter("password")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")           // con CSRF activo, el logout solo funciona por POST
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)    // destruye la sesión del servidor
                .deleteCookies("JSESSIONID")    // borra la cookie de sesión del navegador
                .permitAll()
            )
            // CSRF activo: los formularios con th:action reciben el token automáticamente.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**")) // PROVISIONAL (Fase 3): la Fase 4 definirá la seguridad de la API
            .exceptionHandling(e -> e.accessDeniedPage("/403"));

        return http.build();
    }
}
