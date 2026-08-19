package com.journy.backend.feedback.repository;

import com.journy.backend.feedback.model.TasteFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TasteFeedbackRepository extends JpaRepository<TasteFeedback, String> {
    List<TasteFeedback> findTop80ByUserEmailIgnoreCaseOrderByCreatedAtDesc(String email);
}
