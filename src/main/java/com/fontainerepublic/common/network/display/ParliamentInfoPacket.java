package com.fontainerepublic.common.network.display;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Objects;

/**
 * S2C presentation payload: one public summary snapshot of the parliament
 * proposals (FR-CLIENT-001-A §4.2, message ledger ID 6; FR-CLIENT-001-IMPL-B3a).
 *
 * <p>Sent once on login (institution data changes rarely; live change pushes
 * are a later stage). Carries display data only — never an authority
 * decision. Each proposal entry is bounded ({@code id} ≤ 64, {@code stage}
 * ≤ 32, {@code normLevel} ≤ 32, {@code title} ≤ 128), the list is capped at
 * {@value #MAX_PROPOSALS} entries and {@code at} is the snapshot time. All
 * bounds are enforced at construction and at decode so the wire never carries
 * an out-of-range value.</p>
 */
public record ParliamentInfoPacket(
        List<ProposalEntry> proposals,
        long at
) {

    public static final int MAX_PROPOSALS = 128;

    public ParliamentInfoPacket {
        proposals = Objects.requireNonNull(proposals, "proposals");
        if (proposals.size() > MAX_PROPOSALS) {
            throw new NetworkPayloadException(
                    "Proposals exceed " + MAX_PROPOSALS + ": " + proposals.size()
            );
        }
        proposals = List.copyOf(proposals);
        for (ProposalEntry entry : proposals) {
            Objects.requireNonNull(entry, "proposal entry");
        }
        if (at <= 0) {
            throw new NetworkPayloadException("at must be a positive timestamp: " + at);
        }
    }

    public int count() {
        return proposals.size();
    }

    public static void encode(ParliamentInfoPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        NetworkPayloadLimits.writeCollection(
                buffer,
                message.proposals(),
                MAX_PROPOSALS,
                (buf, entry) -> ProposalEntry.encode(entry, buf)
        );
        buffer.writeLong(message.at());
    }

    public static ParliamentInfoPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        List<ProposalEntry> proposals =
                NetworkPayloadLimits.readList(buffer, MAX_PROPOSALS, ProposalEntry::decode);
        long at = buffer.readLong();
        return new ParliamentInfoPacket(proposals, at);
    }

    /**
     * One proposal summary entry (internal id digest, legislative stage,
     * norm level, title).
     */
    public record ProposalEntry(
            String id,
            String stage,
            String normLevel,
            String title
    ) {

        public static final int MAX_ID = 64;
        public static final int MAX_STAGE = 32;
        public static final int MAX_NORM_LEVEL = 32;
        public static final int MAX_TITLE = 128;

        public ProposalEntry {
            id = Objects.requireNonNull(id, "id");
            if (id.isEmpty() || id.length() > MAX_ID) {
                throw new NetworkPayloadException(
                        "Proposal id must be 1.." + MAX_ID + " characters"
                );
            }
            stage = Objects.requireNonNull(stage, "stage");
            if (stage.isEmpty() || stage.length() > MAX_STAGE) {
                throw new NetworkPayloadException(
                        "Proposal stage must be 1.." + MAX_STAGE + " characters"
                );
            }
            normLevel = Objects.requireNonNull(normLevel, "normLevel");
            if (normLevel.isEmpty() || normLevel.length() > MAX_NORM_LEVEL) {
                throw new NetworkPayloadException(
                        "Proposal normLevel must be 1.." + MAX_NORM_LEVEL + " characters"
                );
            }
            title = Objects.requireNonNull(title, "title");
            if (title.isEmpty() || title.length() > MAX_TITLE) {
                throw new NetworkPayloadException(
                        "Proposal title must be 1.." + MAX_TITLE + " characters"
                );
            }
        }

        public static void encode(ProposalEntry entry, FriendlyByteBuf buffer) {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(buffer, "buffer");
            NetworkPayloadLimits.writeUtf(buffer, entry.id(), MAX_ID);
            NetworkPayloadLimits.writeUtf(buffer, entry.stage(), MAX_STAGE);
            NetworkPayloadLimits.writeUtf(buffer, entry.normLevel(), MAX_NORM_LEVEL);
            NetworkPayloadLimits.writeUtf(buffer, entry.title(), MAX_TITLE);
        }

        public static ProposalEntry decode(FriendlyByteBuf buffer) {
            Objects.requireNonNull(buffer, "buffer");
            String id = NetworkPayloadLimits.readUtf(buffer, MAX_ID);
            String stage = NetworkPayloadLimits.readUtf(buffer, MAX_STAGE);
            String normLevel = NetworkPayloadLimits.readUtf(buffer, MAX_NORM_LEVEL);
            String title = NetworkPayloadLimits.readUtf(buffer, MAX_TITLE);
            return new ProposalEntry(id, stage, normLevel, title);
        }
    }
}
