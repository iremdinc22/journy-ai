package com.journy.backend.destination.service;

import com.journy.backend.common.exception.ResourceNotFoundException;
import com.journy.backend.destination.dto.DestinationResponse;
import com.journy.backend.destination.mapper.DestinationMapper;
import com.journy.backend.destination.repository.DestinationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DestinationService {
    private final DestinationRepository destinationRepository;
    private final DestinationMapper destinationMapper;

    public DestinationService(DestinationRepository destinationRepository, DestinationMapper destinationMapper) {
        this.destinationRepository = destinationRepository;
        this.destinationMapper = destinationMapper;
    }

    @Transactional(readOnly = true)
    public List<DestinationResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return destinationRepository.findTop12ByOrderByPopularDescAvailableDescNameAsc().stream()
                    .map(destinationMapper::toResponse)
                    .toList();
        }

        return destinationRepository
                .findTop12ByNameContainingIgnoreCaseOrCountryContainingIgnoreCaseOrderByAvailableDescNameAsc(query.trim(), query.trim())
                .stream()
                .map(destinationMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DestinationResponse> popular() {
        return destinationRepository.findTop8ByPopularTrueAndAvailableTrueOrderByNameAsc().stream()
                .map(destinationMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DestinationResponse detail(String id) {
        return destinationRepository.findById(id)
                .map(destinationMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Destination was not found"));
    }
}
