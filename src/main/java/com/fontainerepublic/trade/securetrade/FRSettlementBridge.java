package com.fontainerepublic.trade.securetrade;

import com.fontainerepublic.server.audit.api.AuditDraft;
import com.fontainerepublic.server.audit.api.AuditService;
import com.fontainerepublic.server.audit.model.AuditActorType;
import com.fontainerepublic.server.audit.model.AuditCategory;
import com.fontainerepublic.server.audit.model.AuditClassification;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.economy.api.TradeSettlementReceipt;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.model.SubjectId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * FR-TRADE-004 bridge: routes the copied Secure Trade escrow settlement into
 * the server-authoritative FR economy / identity / audit surfaces.
 *
 * <p>The Secure Trade session is a verbatim MIT port; this thin service locator
 * is the single place where it reaches into FR authority. The bridge is bound
 * once at FR runtime start (the services are only resolvable after the module
 * runtime starts). A missing economy service fails settlement closed (the
 * escrow refunds and the trade cancels); identity/audit are best-effort.</p>
 */
public final class FRSettlementBridge {

    private static volatile EconomyService economy;
    private static volatile SubjectRegistryService subjectRegistry;
    private static volatile AuditService audit;
    private static volatile int taxRatePercent = 5;

    private static final String SETTLEMENT_MEMO = "secure trade settlement";

    private FRSettlementBridge() {
    }

    public static void bind(EconomyService economyService, SubjectRegistryService subjects, AuditService auditService, int taxRate) {
        economy = Objects.requireNonNull(economyService, "economyService");
        subjectRegistry = subjects;
        audit = auditService;
        taxRatePercent = Math.max(0, Math.min(100, taxRate));
    }

    public static boolean isSettlementAvailable() {
        return economy != null;
    }

    /**
     * Authoritative FR currency settlement between the two traded subjects
     * (offer legs + 5% payer tax via {@code executeTradeSettlement}). Returns
     * {@code null} when neither side offers money (pure item/XP trade).
     */
    public static TradeSettlementReceipt settleMoney(
            UUID initiatorId,
            UUID partnerId,
            long initiatorMoney,
            long partnerMoney
    ) {
        if (initiatorMoney == 0L && partnerMoney == 0L) {
            return null;
        }
        EconomyService service = economy;
        if (service == null) {
            throw new IllegalStateException("Economy service is not bound");
        }
        SubjectId a = service.ensureAccountForPlayer(initiatorId).subjectId();
        SubjectId b = service.ensureAccountForPlayer(partnerId).subjectId();
        return service.executeTradeSettlement(
                a, b, initiatorMoney, partnerMoney, taxRatePercent, SETTLEMENT_MEMO
        );
    }

    /** Resolves a player UUID to its subject-registry canonical key ("" when unavailable). */
    public static String subjectKeyOf(UUID playerId) {
        SubjectRegistryService registry = subjectRegistry;
        if (registry == null) {
            return "";
        }
        try {
            Optional<com.fontainerepublic.server.registry.model.SubjectRecord> record =
                    registry.findSubjectForPlayer(playerId);
            return record.map(r -> r.subjectId().canonicalKey()).orElse("");
        } catch (RuntimeException failure) {
            return "";
        }
    }

    /**
     * Best-effort authoritative audit (FINANCE {@code trade-settled}). Never
     * throws: an audit failure must not roll back an already-committed trade.
     */
    public static void recordAudit(UUID initiatorId, UUID partnerId, boolean hadMoney, boolean hadItems, boolean hadXp) {
        AuditService service = audit;
        if (service == null) {
            return;
        }
        try {
            String targetId = subjectKeyOf(partnerId);
            AuditDraft draft = new AuditDraft(
                    AuditActorType.PLAYER,
                    initiatorId.toString(),
                    AuditCategory.FINANCE,
                    "trade",
                    "trade-settled",
                    Optional.of("player"),
                    Optional.of(targetId.isEmpty() ? partnerId.toString() : targetId),
                    AuditClassification.AUTHORIZED_SUMMARY,
                    "Secure trade settlement (money=" + hadMoney + ", items=" + hadItems + ", xp=" + hadXp + ")",
                    Optional.empty()
            );
            service.recordAuthoritative(draft);
        } catch (RuntimeException failure) {
            // Audit is best-effort for a committed trade; log and move on.
        }
    }
}
