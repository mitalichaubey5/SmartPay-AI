package com.smartpay.security;

import com.smartpay.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Access to the authenticated user for services (ownership checks).
 */
@Component
public class CurrentUserProvider {

    /**
     * @return the authenticated user
     * @throws UnauthorizedException when no valid token is present
     */
    public AuthenticatedUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new UnauthorizedException("Authentication required");
    }
}
