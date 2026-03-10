package ru.athena.library_demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http

                .authorizeHttpRequests(request -> request
                        .requestMatchers(HttpMethod.GET,"/books/**").hasAnyRole("READER","LIBRARIAN")
                        .requestMatchers(HttpMethod.POST,"/books/**").hasAnyRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.PUT,"/books/**").hasAnyRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.DELETE,"/books/**").hasAnyRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.GET,"/books").hasAnyRole("READER","LIBRARIAN")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults());
        return http.csrf(AbstractHttpConfigurer::disable).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        List<UserDetails> usersList = new ArrayList<>();
        usersList.add(User.withUsername("Jack").password(encoder.encode("password")).roles("READER").build());
        usersList.add(User.withUsername("Jill").password(encoder.encode("password")).roles("READER").build());
        usersList.add(User.withUsername("Admin").password(encoder.encode("password")).roles("READER", "LIBRARIAN").build());
        return new InMemoryUserDetailsManager(usersList);
    }
}