package com.journy.backend.ai;

import com.journy.backend.ai.dto.AiChatRequest;
import com.journy.backend.ai.dto.AiChatResponse;
import com.journy.backend.ai.repository.AiConversationRepository;
import com.journy.backend.ai.service.AiService;
import com.journy.backend.trip.enums.BudgetMode;
import com.journy.backend.trip.enums.TravelInterest;
import com.journy.backend.trip.enums.TravelerType;
import com.journy.backend.trip.enums.TripPace;
import com.journy.backend.trip.model.Trip;
import com.journy.backend.trip.repository.TripRepository;
import com.journy.backend.user.model.UserAccount;
import com.journy.backend.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AiServiceTest {
    @Autowired
    private AiService aiService;
    @Autowired
    private AiConversationRepository aiConversationRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    void chatStoresConversationAndMessages() {
        UserAccount user = userAccountRepository.save(new UserAccount(
                "AI User",
                "ai-" + System.nanoTime() + "@journy.app",
                passwordEncoder.encode("secret"),
                "Balanced traveler"
        ));
        Trip trip = tripRepository.save(new Trip(
                user,
                "Amsterdam",
                "Jordaan",
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 12),
                TravelerType.SOLO,
                BudgetMode.LEAN,
                TripPace.RELAXED,
                Set.of(TravelInterest.COFFEE, TravelInterest.WALKING)
        ));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, Collections.emptyList())
        );

        AiChatResponse response = aiService.chat(new AiChatRequest(trip.getId(), "Find coffee nearby"));

        assertThat(response.conversationId()).isNotBlank();
        assertThat(response.suggestedAction()).isEqualTo("Add coffee stop");
        assertThat(aiConversationRepository.findById(response.conversationId()))
                .get()
                .satisfies(conversation -> assertThat(conversation.getMessages()).hasSize(2));
    }
}
