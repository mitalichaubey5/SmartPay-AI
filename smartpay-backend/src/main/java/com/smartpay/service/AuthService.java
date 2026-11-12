package com.smartpay.service;

import com.smartpay.dto.request.LoginRequest;
import com.smartpay.dto.request.RegisterRequest;
import com.smartpay.dto.response.TokenResponse;
import com.smartpay.dto.response.UserResponse;
import com.smartpay.entity.User;
import com.smartpay.exception.BadRequestException;
import com.smartpay.exception.NotFoundException;
import com.smartpay.repository.UserRepository;
import com.smartpay.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login. Passwords are stored as BCrypt hashes only.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email().trim().toLowerCase())) {
            throw new BadRequestException("An account with this email already exists");
        }
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName() == null ? null : request.fullName().trim());
        user = userRepository.save(user);
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadRequestException("Invalid email or password");
        }
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return UserResponse.from(userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found")));
    }

    private TokenResponse tokenFor(User user) {
        return new TokenResponse(
                jwtService.createToken(user.getId(), user.getEmail(), user.getRole().name()),
                86400,
                UserResponse.from(user));
    }
}
