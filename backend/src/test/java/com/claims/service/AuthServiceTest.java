package com.claims.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.claims.dto.LoginRequest;
import com.claims.dto.RegisterRequest;
import com.claims.entity.Role;
import com.claims.entity.User;
import com.claims.exception.BusinessException;
import com.claims.repository.UserRepository;
import com.claims.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AuthService service = new AuthService(users, passwordEncoder, jwtService);

    @Test
    void registersNormalizedClaimantAndReturnsBearerToken() {
        when(users.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded-password");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("signed-token");
        when(jwtService.getExpirationMs()).thenReturn(60_000L);

        var response = service.register(new RegisterRequest(" Jane@Example.com ", "Password123!", " Jane Doe "));

        assertEquals("jane@example.com", response.email());
        assertEquals("Jane Doe", response.fullName());
        assertEquals(Role.CLAIMANT, response.role());
        assertEquals("signed-token", response.token());
        assertEquals("Bearer", response.tokenType());
        verify(users).save(argThat(user -> user.getEmail().equals("jane@example.com")
                && user.getFullName().equals("Jane Doe")
                && user.getPassword().equals("encoded-password")
                && user.getRole() == Role.CLAIMANT));
    }

    @Test
    void rejectsDuplicateRegistrationWithoutSaving() {
        when(users.existsByEmailIgnoreCase("jane@example.com")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(new RegisterRequest("Jane@example.com", "Password123!", "Jane Doe")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(users, never()).save(any());
    }

    @Test
    void logsInWithTrimmedEmailAndCorrectPassword() {
        User user = new User("jane@example.com", "encoded-password", "Jane Doe", Role.CLAIMANT);
        when(users.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "encoded-password")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("signed-token");
        when(jwtService.getExpirationMs()).thenReturn(60_000L);

        var response = service.login(new LoginRequest(" jane@example.com ", "Password123!"));

        assertEquals("signed-token", response.token());
        assertEquals("jane@example.com", response.email());
    }

    @Test
    void rejectsUnknownEmailAndIncorrectPasswordWithSameUnauthorizedError() {
        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
        BusinessException unknownUser = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("missing@example.com", "Password123!")));

        User user = new User("jane@example.com", "encoded-password", "Jane Doe", Role.CLAIMANT);
        when(users.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword!", "encoded-password")).thenReturn(false);
        BusinessException wrongPassword = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("jane@example.com", "WrongPassword!")));

        assertEquals(HttpStatus.UNAUTHORIZED, unknownUser.getStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, wrongPassword.getStatus());
        assertEquals(unknownUser.getMessage(), wrongPassword.getMessage());
    }
}
