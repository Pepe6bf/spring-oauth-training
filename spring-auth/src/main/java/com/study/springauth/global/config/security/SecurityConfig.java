package com.study.springauth.global.config.security;

import com.study.springauth.domain.auth.entity.RoleType;
import com.study.springauth.domain.auth.jwt.AuthTokenProvider;
import com.study.springauth.domain.auth.jwt.UserRefreshTokenRepository;
import com.study.springauth.domain.auth.jwt.filter.CustomAccessDeniedHandler;
import com.study.springauth.domain.auth.jwt.filter.CustomAuthenticationEntryPoint;
import com.study.springauth.domain.auth.jwt.filter.TokenAuthenticationFilter;
import com.study.springauth.domain.auth.oauth.handler.OAuth2AuthenticationFailureHandler;
import com.study.springauth.domain.auth.oauth.handler.OAuth2AuthenticationSuccessHandler;
import com.study.springauth.domain.auth.oauth.repository.OAuth2AuthorizationRequestBasedOnCookieRepository;
import com.study.springauth.domain.auth.oauth.service.CustomOAuth2UserService;
import com.study.springauth.global.config.properties.AppProperties;
import com.study.springauth.global.config.properties.CorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
public class SecurityConfig {

    private final CorsProperties corsProperties;
    private final AppProperties appProperties;
    private final AuthTokenProvider tokenProvider;
    private final CustomOAuth2UserService oAuth2UserService;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;
    private final UserRefreshTokenRepository userRefreshTokenRepository;

    // ????????? ??????
    public SecurityConfig(
            CorsProperties corsProperties,
            AppProperties appProperties,
            AuthTokenProvider tokenProvider,
            CustomOAuth2UserService oAuth2UserService,
            CustomAccessDeniedHandler customAccessDeniedHandler,
            UserRefreshTokenRepository userRefreshTokenRepository) {
        this.corsProperties = corsProperties;
        this.appProperties = appProperties;
        this.tokenProvider = tokenProvider;
        this.oAuth2UserService = oAuth2UserService;
        this.customAccessDeniedHandler = customAccessDeniedHandler;
        this.userRefreshTokenRepository = userRefreshTokenRepository;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // cors ??????
        http.cors();

        // ????????? ???????????? ?????? ?????????, ?????? ????????? STATELESS ??? ??????
        http.sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        // token ??? ???????????? ???????????? ????????? csrf ??? disable ??????.
        http.csrf().disable()
                .formLogin().disable()
                .httpBasic().disable();

        http.exceptionHandling()
                // ?????? ?????? ???????????? ????????? ????????? ??????
                .authenticationEntryPoint(new CustomAuthenticationEntryPoint())
                // 401, 403 ?????? ???????????? ????????? ????????? ???????????? ?????????
                .accessDeniedHandler(customAccessDeniedHandler);

        // ????????? ?????? ??????
        http.authorizeRequests()
                .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                .antMatchers("/api/v1/auth/refresh").permitAll()
                .antMatchers("/api/**").hasAnyAuthority(RoleType.USER.getCode())
                .antMatchers("/api/**/host/**").hasAnyAuthority(RoleType.HOST.getCode())
                .antMatchers("/api/**/admin/**").hasAnyAuthority(RoleType.ADMIN.getCode())
                // ???????????? ?????? ?????? ??????
                .anyRequest().authenticated();

        // front ?????? login ??? ????????? url
        http.oauth2Login()
                .authorizationEndpoint()
                .baseUri("/oauth2/authorization")
                .authorizationRequestRepository(oAuth2AuthorizationRequestBasedOnCookieRepository());

        // OAuth Server ??????????????? ??????
        http.oauth2Login()
                .redirectionEndpoint()
                .baseUri("/*/oauth2/code/*");

        // ?????? ??? user ????????? ????????? ????????? ??????
        http.oauth2Login()
                .userInfoEndpoint()
                .userService(oAuth2UserService);

        // OAuth2 ??????/?????? ??? ?????? ??? ????????? ??????
        http.oauth2Login()
                .successHandler(oAuth2AuthenticationSuccessHandler())
                .failureHandler(oAuth2AuthenticationFailureHandler());

//         login ??? ????????? ?????? ?????? ??????
        http.addFilterBefore(tokenAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /*
     * security ?????? ???, PW ???????????? ????????? ????????? ??????
     * */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*
     * auth ????????? ??????
     * */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /*
     * ?????? ?????? ??????
     * */
    @Bean
    public TokenAuthenticationFilter tokenAuthenticationFilter() {
        return new TokenAuthenticationFilter(tokenProvider);
    }

    /*
     * ?????? ?????? ?????? Repository
     * ?????? ????????? ?????? ?????? ????????? ??? ??????.
     * */
    @Bean
    public OAuth2AuthorizationRequestBasedOnCookieRepository oAuth2AuthorizationRequestBasedOnCookieRepository() {
        return new OAuth2AuthorizationRequestBasedOnCookieRepository();
    }

    /*
     * Oauth ?????? ?????? ?????????
     * */
    @Bean
    public OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler() {
        return new OAuth2AuthenticationSuccessHandler(
                tokenProvider,
                appProperties,
                userRefreshTokenRepository,
                oAuth2AuthorizationRequestBasedOnCookieRepository()
        );
    }

    /*
     * Oauth ?????? ?????? ?????????
     * */
    @Bean
    public OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler() {
        return new OAuth2AuthenticationFailureHandler(oAuth2AuthorizationRequestBasedOnCookieRepository());
    }

    /*
     * Cors ??????
     * */
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource corsConfigSource = new UrlBasedCorsConfigurationSource();

        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedHeaders(Arrays.asList(corsProperties.getAllowedHeaders().split(",")));
        corsConfig.setAllowedMethods(Arrays.asList(corsProperties.getAllowedMethods().split(",")));
        corsConfig.setAllowedOrigins(Arrays.asList(corsProperties.getAllowedOrigins().split(",")));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(corsConfig.getMaxAge());

        corsConfigSource.registerCorsConfiguration("/**", corsConfig);
        return corsConfigSource;
    }

}