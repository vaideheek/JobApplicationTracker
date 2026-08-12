package com.jobtrack.service;

import com.jobtrack.entity.User;
import com.jobtrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new InsufficientAuthenticationException("User is not authenticated");
        }
        String username = authentication.getName();
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new InsufficientAuthenticationException("Authenticated user not found in database"));
    }

    public void verifyNotDemo() {
        User user = getCurrentUser();
        if (user.isDemoAccount()) {
            throw new AccessDeniedException("Demo accounts cannot perform write operations.");
        }
    }
}
