package com.journy.backend.destination.provider;

import java.util.Optional;

public interface DestinationProvider {
    String name();

    Optional<DestinationCandidate> resolve(String query);
}
