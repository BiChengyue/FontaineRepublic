package com.fontainerepublic.server.government.api;

import com.fontainerepublic.server.government.model.PositionId;
import com.fontainerepublic.server.government.model.PositionState;
import com.fontainerepublic.server.registry.model.OwnerReference;

import java.util.Objects;
import java.util.Optional;

/**
 * Bounded read projection of a government position (FR-GOV-001-A §4; no NBT,
 * no repository, no mutation surface). The holder, when present, is the
 * resolved {@link OwnerReference} — never a game name.
 */
public record PositionProjection(
        PositionId positionId,
        String title,
        PositionState state,
        Optional<OwnerReference> holderRef
) {

    public PositionProjection {
        positionId = Objects.requireNonNull(positionId, "positionId");
        title = Objects.requireNonNull(title, "title");
        state = Objects.requireNonNull(state, "state");
        holderRef = holderRef == null ? Optional.empty() : holderRef;
        if (state == PositionState.FILLED && holderRef.isEmpty()) {
            throw new IllegalArgumentException(
                    "a FILLED position projection must carry a holder"
            );
        }
        if (state != PositionState.FILLED && holderRef.isPresent()) {
            throw new IllegalArgumentException(
                    "a non-FILLED position projection must not carry a holder"
            );
        }
    }
}
