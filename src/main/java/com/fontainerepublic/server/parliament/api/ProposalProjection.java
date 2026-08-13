package com.fontainerepublic.server.parliament.api;

import com.fontainerepublic.server.parliament.model.BillState;
import com.fontainerepublic.server.parliament.model.NormLevel;
import com.fontainerepublic.server.parliament.model.Proposal;
import com.fontainerepublic.server.parliament.model.ProposalId;
import com.fontainerepublic.server.parliament.model.ProposalKind;

/**
 * Bounded, projection-safe view of one proposal for ordered listing
 * (FR-PAR-001-A §4; no enumeration API — this is an exact, bounded
 * projection of a single record).
 */
public record ProposalProjection(
        ProposalId proposalId,
        long proposalSeq,
        String title,
        NormLevel normLevel,
        ProposalKind kind,
        BillState state,
        long createdAt,
        long recordRevision
) {

    public ProposalProjection {
        if (proposalId == null) {
            throw new IllegalArgumentException("proposalId must not be null");
        }
        if (title == null) {
            throw new IllegalArgumentException("title must not be null");
        }
        if (normLevel == null) {
            throw new IllegalArgumentException("normLevel must not be null");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (proposalSeq < 0 || createdAt <= 0 || recordRevision <= 0) {
            throw new IllegalArgumentException("projection numbers are out of bounds");
        }
    }

    public static ProposalProjection from(Proposal proposal) {
        return new ProposalProjection(
                proposal.proposalId(),
                proposal.proposalSeq(),
                proposal.title(),
                proposal.normLevel(),
                proposal.kind(),
                proposal.state(),
                proposal.createdAt(),
                proposal.recordRevision()
        );
    }
}
