package com.service.authorization.config

import com.service.authorization.federatedIdentity.FederatedIdentityConfigurer
import com.service.authorization.token.TokenCustomizer
import com.service.authorization.oauth.CustomOAuth2UserService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import java.util.*

@EnableWebSecurity
@Configuration
class SecurityConfig(
        private val clientRegistrationRepository: ClientRegistrationRepository,
        private val customerOAuth2UserService: OAuth2UserService<OidcUserRequest, OidcUser>,
        private val userNameAndPasswordService: UserDetailsService,
        private val jwtDecoder: JwtDecoder
) {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Throws(java.lang.Exception::class)
    fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http)
        http.getConfigurer(OAuth2AuthorizationServerConfigurer::class.java)
                .oidc(Customizer.withDefaults()) // Enable OpenID Connect 1.0
        http.exceptionHandling { exceptions ->
            exceptions
                    .authenticationEntryPoint(
                            LoginUrlAuthenticationEntryPoint("/login"))
        }
        http.apply(FederatedIdentityConfigurer(clientRegistrationRepository, customerOAuth2UserService))
        http.oauth2ResourceServer { it.jwt().decoder(jwtDecoder) }
        return http.build()
    }


    @Bean
    @Order(2)
    @Throws(java.lang.Exception::class)
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.authorizeHttpRequests { authorize ->
            authorize
                    .requestMatchers("/assets/**", "/webjars/**", "/", "", "/login/**").permitAll()
                    .anyRequest().authenticated()
        }
                .formLogin()
                .defaultSuccessUrl("/console/frame")
                .and()
                .userDetailsService(userNameAndPasswordService)
                .oauth2ResourceServer { it.jwt().decoder(jwtDecoder) }
        http.apply(FederatedIdentityConfigurer(clientRegistrationRepository, customerOAuth2UserService))
        return http.build()
    }

    @Bean
    fun authorizationServerSettings(): AuthorizationServerSettings {
        return AuthorizationServerSettings.builder()
                .issuer("http://localhost:9090")
                .authorizationEndpoint("/oauth2/v1/authorize")
                .tokenEndpoint("/oauth2/v1/token")
                .tokenIntrospectionEndpoint("/oauth2/v1/introspect")
                .tokenRevocationEndpoint("/oauth2/v1/revoke")
                .jwkSetEndpoint("/oauth2/v1/jwks")
                .oidcUserInfoEndpoint("/connect/v1/userinfo")
                .oidcClientRegistrationEndpoint("/connect/v1/register")
                .build()
    }

    /**
     * 회원 승인 정보 저장 서비스
     */
    @Bean
    fun authorizationService(jdbcTemplate: JdbcTemplate, registeredClientRepository: RegisteredClientRepository): OAuth2AuthorizationService {
        return JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository)
    }

    /**
     * 토큰 클레임 커스텀 소스
     */
    @Bean
    fun tokenCustomizer(customerOAuth2UserService: CustomOAuth2UserService):
            OAuth2TokenCustomizer<JwtEncodingContext> {
        return TokenCustomizer(customerOAuth2UserService)
    }
}
