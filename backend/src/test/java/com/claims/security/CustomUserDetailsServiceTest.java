package com.claims.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.claims.entity.Role;
import com.claims.entity.User;
import com.claims.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class CustomUserDetailsServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final CustomUserDetailsService service = new CustomUserDetailsService(users);

    @Test
    void loadsCaseInsensitiveEmailWithEncodedPasswordAndRole() {
        User user = new User("jane@example.com", "encoded-password", "Jane Doe", Role.OFFICER);
        when(users.findByEmailIgnoreCase("JANE@example.com")).thenReturn(Optional.of(user));

        var details = service.loadUserByUsername("JANE@example.com");

        assertEquals("jane@example.com", details.getUsername());
        assertEquals("encoded-password", details.getPassword());
        assertTrue(details.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_OFFICER")));
    }

    @Test
    void throwsUsernameNotFoundWhenEmailDoesNotExist() {
        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("missing@example.com"));
    }
}
