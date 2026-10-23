package com.claims.config;

import com.claims.entity.Role;
import com.claims.entity.User;
import com.claims.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class BootstrapUsers {
    @Bean
    CommandLineRunner createBootstrapUsers(UserRepository users, PasswordEncoder encoder,
            @Value("${app.bootstrap.enabled:true}") boolean enabled,
            @Value("${app.bootstrap.password:ChangeMe123!}") String password) {
        return args -> {
            if (!enabled) return;
            createIfMissing(users, encoder, "manager@claims.local", "Claims Manager", Role.MANAGER, password);
            createIfMissing(users, encoder, "officer@claims.local", "Claims Officer", Role.OFFICER, password);
        };
    }

    private void createIfMissing(UserRepository users, PasswordEncoder encoder, String email, String name,
                                 Role role, String password) {
        if (!users.existsByEmailIgnoreCase(email)) {
            users.save(new User(email, encoder.encode(password), name, role));
        }
    }
}
