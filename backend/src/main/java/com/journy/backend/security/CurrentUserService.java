package com.journy.backend.security;

import com.journy.backend.common.exception.ResourceNotFoundException;
import com.journy.backend.user.model.UserAccount;
import com.journy.backend.user.repository.UserAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final UserAccountRepository userAccountRepository;

    public CurrentUserService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    // Call only inside the state-changing transaction, before reading mutable state.
    public UserAccount currentUserForUpdate() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) throw new ResourceNotFoundException("Authenticated user was not found");
        return userAccountRepository.findForUpdateByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user was not found"));
    }

    public UserAccount currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ResourceNotFoundException("Authenticated user was not found");
        }
        return userAccountRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user was not found"));
    }
}
