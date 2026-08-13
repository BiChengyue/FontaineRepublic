package com.fontainerepublic.common.network.display;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Objects;

/**
 * S2C presentation payload: one public summary snapshot of the court cases
 * (FR-CLIENT-001-A §4.2, message ledger ID 7; FR-CLIENT-001-IMPL-B3b).
 *
 * <p>Sent once on login (institution data changes rarely; live change pushes
 * are a later stage). Carries display data only — never an authority
 * decision. Each case entry is bounded ({@code id} ≤ 64, {@code stage} ≤ 32,
 * {@code summary} ≤ 128), the list is capped at {@value #MAX_CASES} entries
 * and {@code at} is the snapshot time. All bounds are enforced at construction
 * and at decode so the wire never carries an out-of-range value.</p>
 */
public record JusticeInfoPacket(
        List<CaseEntry> cases,
        long at
) {

    public static final int MAX_CASES = 128;

    public JusticeInfoPacket {
        cases = Objects.requireNonNull(cases, "cases");
        if (cases.size() > MAX_CASES) {
            throw new NetworkPayloadException(
                    "Cases exceed " + MAX_CASES + ": " + cases.size()
            );
        }
        cases = List.copyOf(cases);
        for (CaseEntry entry : cases) {
            Objects.requireNonNull(entry, "case entry");
        }
        if (at <= 0) {
            throw new NetworkPayloadException("at must be a positive timestamp: " + at);
        }
    }

    public int count() {
        return cases.size();
    }

    public static void encode(JusticeInfoPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        NetworkPayloadLimits.writeCollection(
                buffer,
                message.cases(),
                MAX_CASES,
                (buf, entry) -> CaseEntry.encode(entry, buf)
        );
        buffer.writeLong(message.at());
    }

    public static JusticeInfoPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        List<CaseEntry> cases =
                NetworkPayloadLimits.readList(buffer, MAX_CASES, CaseEntry::decode);
        long at = buffer.readLong();
        return new JusticeInfoPacket(cases, at);
    }

    /**
     * One case summary entry (internal id digest, judicial stage, case
     * summary/title).
     */
    public record CaseEntry(
            String id,
            String stage,
            String summary
    ) {

        public static final int MAX_ID = 64;
        public static final int MAX_STAGE = 32;
        public static final int MAX_SUMMARY = 128;

        public CaseEntry {
            id = Objects.requireNonNull(id, "id");
            if (id.isEmpty() || id.length() > MAX_ID) {
                throw new NetworkPayloadException(
                        "Case id must be 1.." + MAX_ID + " characters"
                );
            }
            stage = Objects.requireNonNull(stage, "stage");
            if (stage.isEmpty() || stage.length() > MAX_STAGE) {
                throw new NetworkPayloadException(
                        "Case stage must be 1.." + MAX_STAGE + " characters"
                );
            }
            summary = Objects.requireNonNull(summary, "summary");
            if (summary.isEmpty() || summary.length() > MAX_SUMMARY) {
                throw new NetworkPayloadException(
                        "Case summary must be 1.." + MAX_SUMMARY + " characters"
                );
            }
        }

        public static void encode(CaseEntry entry, FriendlyByteBuf buffer) {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(buffer, "buffer");
            NetworkPayloadLimits.writeUtf(buffer, entry.id(), MAX_ID);
            NetworkPayloadLimits.writeUtf(buffer, entry.stage(), MAX_STAGE);
            NetworkPayloadLimits.writeUtf(buffer, entry.summary(), MAX_SUMMARY);
        }

        public static CaseEntry decode(FriendlyByteBuf buffer) {
            Objects.requireNonNull(buffer, "buffer");
            String id = NetworkPayloadLimits.readUtf(buffer, MAX_ID);
            String stage = NetworkPayloadLimits.readUtf(buffer, MAX_STAGE);
            String summary = NetworkPayloadLimits.readUtf(buffer, MAX_SUMMARY);
            return new CaseEntry(id, stage, summary);
        }
    }
}
