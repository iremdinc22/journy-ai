package com.journy.backend.ai.repository;

import com.journy.backend.ai.model.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiConversationRepository extends JpaRepository<AiConversation, String> {
    Optional<AiConversation> findFirstByUserEmailIgnoreCaseAndTripIdOrderByUpdatedAtDesc(String email, String tripId);

    Optional<AiConversation> findFirstByUserEmailIgnoreCaseAndTripIsNullOrderByUpdatedAtDesc(String email);
}
