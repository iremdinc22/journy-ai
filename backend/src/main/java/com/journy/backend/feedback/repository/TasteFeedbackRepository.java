package com.journy.backend.feedback.repository;

import java.util.Optional;

import com.journy.backend.feedback.model.TasteFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TasteFeedbackRepository extends JpaRepository<TasteFeedback, String> {
    Optional<TasteFeedback> findByUserIdAndEventKey(String userId, String eventKey);

    List<TasteFeedback> findTop80ByUserEmailIgnoreCaseOrderByCreatedAtDesc(String email);
}
