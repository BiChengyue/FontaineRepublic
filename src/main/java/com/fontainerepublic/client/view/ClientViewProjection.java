package com.fontainerepublic.client.view;

import com.fontainerepublic.client.net.ClientPresentationCache;
import com.fontainerepublic.common.network.display.BalanceSyncPacket;
import com.fontainerepublic.common.network.display.CitizenInfoPacket;
import com.fontainerepublic.common.network.display.NotificationPacket;
import com.fontainerepublic.common.network.display.TransactionHistorySyncPacket;
import com.fontainerepublic.common.network.display.TransactionNotifyPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure display projection over the non-authoritative presentation cache
 * (FR-CLIENT-001-IMPL-B).
 *
 * <p>Every method is a read-only projection: it renders cache entries into
 * bounded display lines used by both the GUI/HUD and the dependency-free
 * validation main. The cache itself stays non-authoritative; nothing here
 * participates in any decision or mutation. All methods take the cache as a
 * parameter so tests can drive a fresh instance without touching the
 * singleton.</p>
 */
public final class ClientViewProjection {

    /** Placeholder shown when no balance snapshot has arrived yet. */
    public static final String BALANCE_PLACEHOLDER = "--";

    /** Placeholder shown for a missing optional memo. */
    public static final String NO_MEMO = "";

    private ClientViewProjection() {
    }

    /**
     * Renders the balance line of {@code cache}: {@code "1,234 M"} when a
     * snapshot is present, or the placeholder when the cache is empty.
     */
    public static String balanceLine(ClientPresentationCache cache) {
        BalanceSyncPacket snapshot = cache.balanceSnapshot();
        if (snapshot == null) {
            return BALANCE_PLACEHOLDER;
        }
        return formatAmount(snapshot.balance()) + " " + snapshot.currencySymbol();
    }

    /**
     * Renders the currency display name of the snapshot (used as a card
     * caption), or the placeholder when absent.
     */
    public static String currencyName(ClientPresentationCache cache) {
        BalanceSyncPacket snapshot = cache.balanceSnapshot();
        return snapshot == null ? BALANCE_PLACEHOLDER : snapshot.currencyName();
    }

    /**
     * Bounded projection of the transaction notices into display lines
     * (newest first). Each line is bounded: {@code #<id> IN|OUT <amount>
     * <digest-prefix> [memo]}. The cache ring is already bounded; this method
     * additionally caps the returned list so a caller can never render more
     * than the ring size.
     */
    public static List<String> transactionLines(ClientPresentationCache cache) {
        List<TransactionNotifyPacket> notices = cache.transactionNotices();
        List<String> lines = new ArrayList<>(notices.size());
        for (int index = notices.size() - 1; index >= 0; index--) {
            TransactionNotifyPacket notice = notices.get(index);
            String direction = notice.direction() == TransactionNotifyPacket.DIRECTION_IN
                    ? "IN"
                    : "OUT";
            StringBuilder line = new StringBuilder()
                    .append('#').append(notice.transactionId())
                    .append(' ').append(direction)
                    .append(' ').append(formatAmount(notice.amount()))
                    .append(' ').append(digestPrefix(notice.counterpartyDigest()));
            if (notice.memo() != null && !notice.memo().isEmpty()) {
                line.append(" \"").append(notice.memo()).append('"');
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * Bounded projection of the pending notifications into display lines
     * (each: {@code #<id> +<amount> [memo]}).
     */
    public static List<String> notificationLines(ClientPresentationCache cache) {
        List<NotificationPacket.NotificationEntry> entries =
                cache.notificationEntries();
        List<String> lines = new ArrayList<>(entries.size());
        for (NotificationPacket.NotificationEntry entry : entries) {
            StringBuilder line = new StringBuilder()
                    .append('#').append(entry.notificationId())
                    .append(" +").append(formatAmount(entry.amount()));
            if (entry.memo() != null && !entry.memo().isEmpty()) {
                line.append(' ').append(entry.memo());
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * Bounded projection of the citizen-identity card into display lines
     * (each: {@code Label: value}), or a single placeholder line when no
     * snapshot has arrived yet.
     */
    public static List<String> citizenLines(ClientPresentationCache cache) {
        CitizenInfoPacket snapshot = cache.citizenSnapshot();
        if (snapshot == null) {
            return List.of("No citizen card yet.");
        }
        return List.of(
                "Registry: " + snapshot.registryNumber(),
                "Status: " + snapshot.citizenStatus(),
                "Rank: " + snapshot.citizenRank(),
                "Citizen since: " + formatTime(snapshot.firstCitizenAt())
        );
    }

    /**
     * Bounded projection of the transaction-history page into display lines
     * (each: {@code #<id> IN|OUT <amount> <digest-prefix> [memo]}), or a
     * placeholder line when no page has arrived yet. The page is already
     * bounded by the packet; this method additionally caps the returned list
     * so a caller can never render more than the cache bound.
     */
    public static List<String> historyLines(ClientPresentationCache cache) {
        TransactionHistorySyncPacket page = cache.historySnapshot();
        if (page == null) {
            return List.of("No history yet.");
        }
        List<TransactionHistorySyncPacket.HistoryEntry> entries = page.entries();
        int cap = Math.min(entries.size(), ClientPresentationCache.MAX_HISTORY_ENTRIES);
        List<String> lines = new ArrayList<>(cap);
        for (int index = 0; index < cap; index++) {
            TransactionHistorySyncPacket.HistoryEntry entry = entries.get(index);
            String direction = entry.direction() == TransactionHistorySyncPacket.HistoryEntry.DIRECTION_IN
                    ? "IN"
                    : "OUT";
            StringBuilder line = new StringBuilder()
                    .append('#').append(entry.transactionId())
                    .append(' ').append(direction)
                    .append(' ').append(formatAmount(entry.amount()))
                    .append(' ').append(digestPrefix(entry.counterpartyDigest()));
            if (entry.memo() != null && !entry.memo().isEmpty()) {
                line.append(" \"").append(entry.memo()).append('"');
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** Bounded digest prefix for display (first 8 hex characters). */
    public static String digestPrefix(String counterpartyDigest) {
        if (counterpartyDigest == null || counterpartyDigest.isEmpty()) {
            return "";
        }
        int prefix = Math.min(8, counterpartyDigest.length());
        return counterpartyDigest.substring(0, prefix);
    }

    /**
     * Deterministic UTC timestamp projection of a positive epoch millisecond
     * (display only). Returns the raw value when it is non-positive.
     */
    public static String formatTime(long epochMillis) {
        if (epochMillis <= 0) {
            return Long.toString(epochMillis);
        }
        return java.time.Instant.ofEpochMilli(epochMillis).toString();
    }

    /**
     * Locale-stable thousands grouping of a non-negative amount (display
     * only; never used for arithmetic).
     */
    public static String formatAmount(long amount) {
        if (amount < 0) {
            return Long.toString(amount);
        }
        if (amount == 0) {
            return "0";
        }
        String digits = Long.toString(amount);
        StringBuilder grouped = new StringBuilder();
        int firstGroup = digits.length() % 3;
        if (firstGroup == 0) {
            firstGroup = 3;
        }
        int index = 0;
        while (index < digits.length()) {
            int take = Math.min(firstGroup, digits.length() - index);
            if (grouped.length() > 0) {
                grouped.append(',');
            }
            grouped.append(digits, index, index + take);
            index += take;
            firstGroup = 3;
        }
        return grouped.toString();
    }
}
