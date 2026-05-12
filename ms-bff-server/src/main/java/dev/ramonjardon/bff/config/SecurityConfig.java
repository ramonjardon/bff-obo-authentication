package dev.ramonjardon.bff.config;



import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;


@Configuration(proxyBeanMethods=false)
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApplicationConfigProperties applicationConfigProperties;
   







  

@Bean
public SecurityFilterChain filterChain(
        HttpSecurity http,
        ClientRegistrationRepository clientRegistrationRepository) {
    try {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/public/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )

            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            )
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .addFilterBefore(
                new TokenExpiredToUnauthorizedFilter(),
                OAuth2AuthorizationRequestRedirectFilter.class
            )
            .oauth2Login(login -> login
                .defaultSuccessUrl(applicationConfigProperties.getSpaBaseUrl()
                .concat(applicationConfigProperties.getSuccessPath())
                , true)
                .failureHandler((req, res, ex) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.getWriter().write("{\"error\":\"login_failed\"}");
                })
            )
            .logout(logout -> logout
                .logoutUrl(applicationConfigProperties.getLogoutUrl())
                .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
                .deleteCookies("SESSION", "JSESSIONID")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.getWriter().write("{\"error\":\"unauthenticated\"}");
                })
            );

        return http.build();

    } catch (RuntimeException e) {
        throw e;
    } catch (Exception e) {
        throw new IllegalStateException("Error al construir el SecurityFilterChain", e);
    }
}

    @Bean
    public DefaultCookieSerializer cookieSerializer() {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(applicationConfigProperties.isSecureCookie());

        // Extrae el dominio raíz del request automáticamente.
        //
        // Con dominio propio en Cloudflare (tu caso):
        //   "api.tudominio.com"  → Domain=tudominio.com
        //   "app.tudominio.com"  → Domain=tudominio.com  (mismo dominio raíz)
        // Resultado: la cookie SESSION llega a ambos subdominios → SPA la envía.
        //
        // En local (localhost no tiene subdominios → patrón no hace match):
        //   Sin Domain en la cookie → host-only → correcto para desarrollo.
        //
        // Sin este patrón (usando setDomainName fijo), en local la cookie
        // tendría Domain=localhost que el browser no envía en peticiones
        // a 127.0.0.1 o ::1 → sesión rota en desarrollo.
        serializer.setDomainNamePattern("^.+?\\.(.+\\.[a-z]+)$");

        // Lax es correcto para subdominios bajo el mismo dominio raíz.
        //
        // app.tudominio.com → api.tudominio.com: same-site → cookie se envía.
        // Logto → api.tudominio.com (callback OAuth2): cross-site GET top-level
        //   navigation → Lax lo permite (OAuth2 usa redirect GET, no POST).
        //
        // Solo necesitarías None si SPA y BFF estuvieran en dominios raíz
        // distintos (ej: vercel.app vs railway.app sin custom domain).
        serializer.setSameSite("Lax");

        return serializer;
    }

    private OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler(
            ClientRegistrationRepository repo) {
        var handler = new OidcClientInitiatedLogoutSuccessHandler(repo);
        // Debe coincidir con Post-logout Redirect URI en Logto Console
        handler.setPostLogoutRedirectUri(applicationConfigProperties.getSpaBaseUrl());
        return handler;
    }

    // --- Filtros internos como clases estáticas ---

    /**
     * Fuerza la escritura de la cookie XSRF-TOKEN en cada respuesta.
     * CookieCsrfTokenRepository usa un Supplier lazy: sin este filtro,
     * el token no se escribe si nadie lo accede durante la petición.
     * El SPA necesita leer la cookie en el primer GET para poder
     * enviar el token en las peticiones mutantes (POST, PUT, DELETE).
     */
    static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {

            var csrfToken =
                (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                // Fuerza la evaluación del Supplier → escribe la cookie
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Intercepta ClientAuthorizationRequiredException antes de que
     * OAuth2AuthorizationRequestRedirectFilter haga un redirect 302.
     *
     * Problema: cuando el access token y el refresh token han expirado,
     * Spring intenta redirigir al IdP automáticamente. Para peticiones
     * AJAX del SPA (/api/**), ese 302 llega al browser, que lo sigue
     * silenciosamente y el SPA recibe HTML del IdP como respuesta JSON.
     *
     * Solución: para /api/**, devolver 401 con JSON para que el SPA
     * pueda manejarlo (mostrar pantalla de login, etc.).
     * Para navegaciones del browser (no /api/**), dejar que Spring
     * redirija al IdP normalmente.
     */
    static final class TokenExpiredToUnauthorizedFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            try {
                filterChain.doFilter(request, response);
            } catch (ClientAuthorizationRequiredException ex) {
                if (request.getRequestURI().startsWith("/api/")) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"error\":\"session_expired\"}");
                } else {
                    throw ex;
                }
            }
        }
    }

}
