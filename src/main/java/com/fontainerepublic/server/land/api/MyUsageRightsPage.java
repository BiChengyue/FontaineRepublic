package com.fontainerepublic.server.land.api;

import com.fontainerepublic.server.land.model.ParcelId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable bounded page of the authenticated player's own current usage
 * rights (FR-LAND-002-A §3.1/§3.2).
 *
 * <p>This is the single explicitly allowed person-scoped page record: it is
 * never a raw {@code List}/{@code Map}/array/{@code Stream}, and no public
 * API returns a full collection or an arbitrary-holder enumeration. The page
 * carries a closed {@link MyUsageRightsStatus}, the {@code storeRevision}
 * observed at query time, the {@code generatedAt} clock value, at most
 * {@code limit} active {@link MyUsageRightProjection} entries, an exclusive
 * {@code nextAfterParcelId} cursor equal to the <b>last holder-index parcel
 * examined</b> (not merely the last active entry returned, so expired entries
 * never cause skips), and a {@code hasMore} flag.</p>
 */
public record MyUsageRightsPage(
        MyUsageRightsStatus status,
        long storeRevision,
        long generatedAt,
        List<MyUsageRightProjection> entries,
        Optional<ParcelId> nextAfterParcelId,
        boolean hasMore
) {
    public MyUsageRightsPage {
        status = Objects.requireNonNull(status, "status");
        storeRevision = requireNonNegative(storeRevision, "storeRevision");
        generatedAt = requirePositive(generatedAt, "generatedAt");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        nextAfterParcelId =
                Objects.requireNonNull(nextAfterParcelId, "nextAfterParcelId");
        if (status != MyUsageRightsStatus.OK
                && (!entries.isEmpty()
                || nextAfterParcelId.isPresent()
                || hasMore)) {
            throw new IllegalArgumentException(
                    "A non-OK my-usage-rights page must carry zero entries and "
                            + "no cursor/hasMore projection"
            );
        }
    }

    /**
     * Builds an {@code OK} page with the given active projections.
     *
     * @param storeRevision the store revision observed at query time
     * @param generatedAt the server clock at query time (positive epoch millis)
     * @param entries the bounded (≤ limit) active right projections
     * @param lastExamined the last holder-index parcel examined, or empty when
     *        nothing was examined
     * @param hasMore whether further holder-index parcels exist after
     *        {@code lastExamined}
     */
    public static MyUsageRightsPage ok(
            long storeRevision,
            long generatedAt,
            List<MyUsageRightProjection> entries,
            Optional<ParcelId> lastExamined,
            boolean hasMore
    ) {
        return new MyUsageRightsPage(
                MyUsageRightsStatus.OK,
                storeRevision,
                generatedAt,
                entries,
                lastExamined,
                hasMore
        );
    }

    /** A non-OK page: zero entries, no cursor, no hasMore. */
    public static MyUsageRightsPage closed(
            MyUsageRightsStatus status,
            long storeRevision,
            long generatedAt
    ) {
        return new MyUsageRightsPage(
                status,
                storeRevision,
                generatedAt,
                List.of(),
                Optional.empty(),
                false
        );
    }

    private static long requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be a positive epoch millisecond");
        }
        return value;
    }
}
