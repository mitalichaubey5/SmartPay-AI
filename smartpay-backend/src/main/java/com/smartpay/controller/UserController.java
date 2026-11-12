package com.smartpay.controller;

import com.smartpay.dto.response.UserResponse;
import com.smartpay.security.AuthenticatedUser;
import com.smartpay.security.CurrentUserProvider;
import com.smartpay.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Current-user profile endpoint.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;

    public UserController(AuthService authService, CurrentUserProvider currentUserProvider) {
        this.authService = authService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/me")
    public UserResponse me() {
        AuthenticatedUser user = currentUserProvider.require();
        return authService.me(user.userId());
    }
}
