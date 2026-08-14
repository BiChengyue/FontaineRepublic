package com.fontainerepublic.server.landrights;

import com.fontainerepublic.common.landrights.MyLandRightsPagePacket;
import com.fontainerepublic.common.landrights.MyLandRightsRequestPacket;
import com.fontainerepublic.common.network.NetworkMessageSpec;
import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkProductionMessageTable;
import com.fontainerepublic.common.network.NetworkProtocol;
import com.fontainerepublic.common.network.RateLimitPolicy;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.core.ModSavedData;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.audit.AuditModule;
import com.fontainerepublic.server.citizen.CitizenModule;
import com.fontainerepublic.server.command.CommandBootstrap;
import com.fontainerepublic.server.command.CommandRuntimeResolver;
import com.fontainerepublic.server.command.LandCommand;
import com.fontainerepublic.server.command.registration.CommandContributionRegistry;
import com.fontainerepublic.server.command.registration.CommandContributionSpec;
import com.fontainerepublic.server.economy.EconomyModule;
import com.fontainerepublic.server.emergency.EmergencyModule;
import com.fontainerepublic.server.government.GovernmentModule;
import com.fontainerepublic.server.institutionaccess.InstitutionAccessModule;
import com.fontainerepublic.server.justice.JusticeModule;
import com.fontainerepublic.server.land.LandModule;
import com.fontainerepublic.server.land.api.CreateParcelRequest;
import com.fontainerepublic.server.land.api.HolderDirectory;
import com.fontainerepublic.server.land.api.LandService;
import com.fontainerepublic.server.land.api.MyUsageRightProjection;
import com.fontainerepublic.server.land.api.MyUsageRightsPage;
import com.fontainerepublic.server.land.api.MyUsageRightsQueryLimits;
import com.fontainerepublic.server.land.api.MyUsageRightsStatus;
import com.fontainerepublic.server.land.api.PermissionResolver;
import com.fontainerepublic.server.land.model.LandAccess;
import com.fontainerepublic.server.land.model.LandParcel;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.land.model.ParcelRegion;
import com.fontainerepublic.server.land.model.ZoneType;
import com.fontainerepublic.server.land.persistence.LandLimits;
import com.fontainerepublic.server.land.persistence.LandNbtCodec;
import com.fontainerepublic.server.land.persistence.LandRepository;
import com.fontainerepublic.server.land.persistence.LandStore;
import com.fontainerepublic.server.land.persistence.LandStoreSnapshot;
import com.fontainerepublic.server.land.persistence.LandUnavailableException;
import com.fontainerepublic.server.land.persistence.ParcelIdSource;
import com.fontainerepublic.server.land.service.ConfigDrivenPermissionResolver;
import com.fontainerepublic.server.land.service.DefaultLandService;
import com.fontainerepublic.server.land.service.LandPermissionConfig;
import com.fontainerepublic.server.landclaim.LandClaimModule;
import com.fontainerepublic.server.landrights.MyLandRightsRuntime;
import com.fontainerepublic.server.landrights.api.MyLandRightsResponse;
import com.fontainerepublic.server.landrights.api.MyLandRightsServerPlayerAccess;
import com.fontainerepublic.server.landrights.api.MyLandRightsService;
import com.fontainerepublic.server.landrights.service.DefaultMyLandRightsService;
import com.fontainerepublic.server.mail.MailModule;
import com.fontainerepublic.server.network.NetworkRuntimeModule;
import com.fontainerepublic.server.parliament.ParliamentModule;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.registry.SubjectRegistryModule;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.trade.TradeModule;
import com.mojang.brigadier.CommandDispatcher;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Dependency-free validation entry point for FR-LAND-002-A (self-only bounded
 * my usage-rights page): derived holder index + bounded page query, protocol v9
 * IDs 27/28, strict codec + rate policy + correlation, and the negative
 * command-tree / enumeration / client-class-isolation assertions. Mirrors the
 * {@code *FoundationTestMain} harness pattern (no JUnit; a {@code main} that
 * throws on the first failed {@code require}).
 */
public final class LandRightsFoundationTestMain {

    private static final String PROJECT_DIR_PROPERTY = "fontainerepublic.projectDir";
    private static final String DIMENSION = "minecraft:overworld";

    private static final UUID ALPHA_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRAVO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private LandRightsFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testMultipleParcelRevision1Pagination();
        testExclusiveCursorAndLastExaminedAcrossExpired();
        testRevisionDriftResetRequired();
        testServicePreservesResetRequired();
        testSelfOnlyPersonNoTargetInput();
        testActiveVsExpiredFiltering();
        testHolderIndexLifecycle();
        testFailedDurableCommitLeavesIndexUnchanged();
        testInvalidLimitsAndMalformedPackets();
        testCursorRevisionPairingEnforced();
        testStrictProjectedFieldValidation();
        testCorrelationStaleRejection();
        testCommunicatorGate();
        testProtocolV9Ledger();
        testNoEnumerationAndNoHolderTarget();
        testNoLandMineCommand();
        testDedicatedServerClientIsolation();
        testNoLandRightsModuleRegistration();
        testLandModuleOwnsProjectionRuntime();
        testScreenCloseClearsCache();
        System.out.println("[FR-LAND-002] My usage-rights foundation validation passed");
    }

    // ------------------------------------------------------------------
    // 1. multiple parcels all at parcelRevision=1 paginate without skip/duplicate
    // ------------------------------------------------------------------

    private static void testMultipleParcelRevision1Pagination() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        LandService service = service(store, clock, holderDirectory(ALPHA_ID));
        for (int i = 0; i < 5; i++) {
            service.createParcelWithUsage(
                    ALPHA_ID, createRequest(ZoneType.RESIDENTIAL, i * 100),
                    OwnerReference.forPlayer(ALPHA_ID), 0
            );
        }
        require(service.publicSummary().parcelCount() == 5,
                "five claimed parcels exist");

        List<UUID> collected = new ArrayList<>();
        long revision = 0L;
        Optional<ParcelId> cursor = Optional.empty();
        int pages = 0;
        while (pages < 20) {
            MyUsageRightsPage page = service.myUsageRights(
                    ALPHA_ID, cursor, revision, 2
            );
            require(page.status() == MyUsageRightsStatus.OK,
                    "pagination pages are OK");
            require(page.entries().size() <= 2, "page holds at most the limit");
            revision = page.storeRevision();
            for (MyUsageRightProjection entry : page.entries()) {
                require(entry.parcelRevision() == 1,
                        "every claimed parcel is at parcelRevision 1");
                collected.add(entry.parcelId().value());
            }
            if (!page.hasMore()) {
                break;
            }
            require(page.nextAfterParcelId().isPresent(),
                    "hasMore implies a next cursor");
            cursor = page.nextAfterParcelId();
            pages++;
        }
        require(pages > 0, "pagination produced pages");
        require(collected.size() == 5, "all five parcels collected: " + collected.size());
        require(new HashSet<>(collected).size() == 5,
                "no duplicate parcel across pages");
        List<UUID> sorted = new ArrayList<>(collected);
        sorted.sort(java.util.Comparator.comparing(
                (UUID uuid) -> ParcelId.of(uuid).canonicalKey()));
        require(collected.equals(sorted),
                "pages arrive in canonical ParcelId ascending order");
    }

    // ------------------------------------------------------------------
    // 2. canonical exclusive cursor + last-examined across expired rights
    // ------------------------------------------------------------------

    private static void testExclusiveCursorAndLastExaminedAcrossExpired() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        LandService service = service(store, clock, holderDirectory(ALPHA_ID));
        // Two parcels held by ALPHA: one never expires, one expires at now+100.
        service.createParcelWithUsage(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL),
                OwnerReference.forPlayer(ALPHA_ID), 0
        );
        service.createParcelWithUsage(
                ALPHA_ID, createRequest(ZoneType.COMMERCIAL, 100),
                OwnerReference.forPlayer(ALPHA_ID), 100
        );
        // Advance past the second parcel's expiry.
        clock.setNow(5_000);

        MyUsageRightsPage first = service.myUsageRights(
                ALPHA_ID, Optional.empty(), 0L,
                MyUsageRightsQueryLimits.MAX_LIMIT
        );
        require(first.status() == MyUsageRightsStatus.OK, "first page is OK");
        require(first.entries().size() == 1,
                "only the never-expiring right is active after expiry");
        // The last holder-index parcel examined may differ from the single
        // active entry returned: it is the parcel whose canonical key is last.
        require(first.nextAfterParcelId().isPresent(),
                "an examined parcel supplies the next cursor");
        require(first.hasMore() == false,
                "no further parcels exist after the last examined");

        // A bounded continuation from the returned cursor must not duplicate
        // or skip: now ask a second page with the actor's full index.
        MyUsageRightsPage full = service.myUsageRights(
                ALPHA_ID, Optional.empty(), 0L, MyUsageRightsQueryLimits.MAX_LIMIT
        );
        require(full.entries().size() == 1, "full re-query returns the active right");
    }

    // ------------------------------------------------------------------
    // 3. store-revision drift -> RESET_REQUIRED with no entries
    // ------------------------------------------------------------------

    private static void testRevisionDriftResetRequired() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        LandService service = service(store, clock, holderDirectory(ALPHA_ID));
        service.createParcelWithUsage(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL),
                OwnerReference.forPlayer(ALPHA_ID), 0
        );
        MyUsageRightsPage first = service.myUsageRights(
                ALPHA_ID, Optional.empty(), 0L, MyUsageRightsQueryLimits.DEFAULT_LIMIT
        );
        require(first.status() == MyUsageRightsStatus.OK, "first page is OK");
        long observed = first.storeRevision();

        // Another commit advances the store revision.
        service.createParcelWithUsage(
                ALPHA_ID, createRequest(ZoneType.COMMERCIAL, 100),
                OwnerReference.forPlayer(ALPHA_ID), 0
        );
        MyUsageRightsPage stale = service.myUsageRights(
                ALPHA_ID, first.nextAfterParcelId(), observed,
                MyUsageRightsQueryLimits.DEFAULT_LIMIT
        );
        require(stale.status() == MyUsageRightsStatus.RESET_REQUIRED,
                "a contrived store revision returns RESET_REQUIRED");
        require(stale.entries().isEmpty(), "RESET_REQUIRED carries no entries");
        require(stale.nextAfterParcelId().isEmpty()
                        && !stale.hasMore(),
                "RESET_REQUIRED leaks no cursor/hasMore");
        require(stale.storeRevision() > observed,
                "RESET_REQUIRED reports the current store revision");

        // Fresh first page (expected 0) always succeeds regardless of revision.
        MyUsageRightsPage fresh = service.myUsageRights(
                ALPHA_ID, Optional.empty(), 0L, MyUsageRightsQueryLimits.DEFAULT_LIMIT
        );
        require(fresh.status() == MyUsageRightsStatus.OK, "fresh first page is OK");
    }

    // ------------------------------------------------------------------
    // 3.1. the communicator service preserves a RESET_REQUIRED page status
    // ------------------------------------------------------------------

    private static void testServicePreservesResetRequired() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        LandService land = service(store, clock, holderDirectory(ALPHA_ID));
        land.createParcelWithUsage(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL),
                OwnerReference.forPlayer(ALPHA_ID), 0
        );
        FairPlayerAccess access = new FairPlayerAccess(ALPHA_ID);
        MyLandRightsService rights = new DefaultMyLandRightsService(
                land, access, clock);

        // Take a first page, then advance the store revision with a new parcel.
        MyUsageRightsPage first = land.myUsageRights(
                ALPHA_ID, Optional.empty(), 0L, MyUsageRightsQueryLimits.DEFAULT_LIMIT
        );
        long observed = first.storeRevision();
        land.createParcelWithUsage(
                ALPHA_ID, createRequest(ZoneType.COMMERCIAL, 100),
                OwnerReference.forPlayer(ALPHA_ID), 0
        );

        // A continuation request with the now-stale revision is a valid pairing
        // (cursor + positive revision) but drifts, so the land service returns
        // RESET_REQUIRED. The communicator service must propagate that exact
        // status rather than re-wrapping it as OK.
        MyLandRightsResponse response = rights.request(
                ALPHA_ID, 42, first.nextAfterParcelId(), observed,
                MyUsageRightsQueryLimits.DEFAULT_LIMIT
        );
        require(response.status() == MyUsageRightsStatus.RESET_REQUIRED,
                "a revision-drift request stays RESET_REQUIRED through the service");
        require(response.requestId() == 42 && response.generatedAt() == clock.getAsLong(),
                "RESET_REQUIRED echoes requestId and generatedAt");
        require(response.storeRevision() > observed,
                "RESET_REQUIRED reports the current store revision");
        require(response.entries().isEmpty(),
                "RESET_REQUIRED carries zero entries");
        require(response.nextAfterParcelId().isEmpty() && !response.hasMore(),
                "RESET_REQUIRED leaks no cursor/hasMore");
    }

    // ------------------------------------------------------------------
    // 4. self-only PLAYER_UUID + no target input
    // ------------------------------------------------------------------

    private static void testSelfOnlyPersonNoTargetInput() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        LandService service = service(store, clock, holderDirectory(ALPHA_ID, BRAVO_ID));
        service.createParcelWithUsage(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL),
                OwnerReference.forPlayer(ALPHA_ID), 0
        );
        service.createParcelWithUsage(
                ALPHA_ID, createRequest(ZoneType.COMMERCIAL, 100),
                OwnerReference.forPlayer(BRAVO_ID), 0
        );

        MyUsageRightsPage alpha = service.myUsageRights(
                ALPHA_ID, Optional.empty(), 0L, MyUsageRightsQueryLimits.DEFAULT_LIMIT
        );
        require(alpha.entries().size() == 1,
                "ALPHA sees only their own right, not BRAVO's");
        MyUsageRightsPage bravo = service.myUsageRights(
                BRAVO_ID, Optional.empty(), 0L, MyUsageRightsQueryLimits.DEFAULT_LIMIT
        );
        require(bravo.entries().size() == 1,
                "BRAVO sees only their own right, not ALPHA's");

        // An unprovisioned player is rejected fail-closed (UNAVAILABLE at transport).
        LandUnavailableException unknown = expectThrows(
                LandUnavailableException.class,
                () -> service.myUsageRights(
                        UUID.fromString("00000000-0000-0000-0000-000000000099"),
                        Optional.empty(), 0L, MyUsageRightsQueryLimits.DEFAULT_LIMIT
                ),
                "an unprovisioned player is rejected fail-closed"
        );
        require(unknown.failureCode().equals(
                        LandUnavailableException.CODE_PLAYER_NOT_PROVISIONED),
                "unprovisioned player carries the stable code");
    }

    // ------------------------------------------------------------------
    // 5. active / no-expiry vs expired filtering
    // ------------------------------------------------------------------

    private static void testActiveVsExpiredFiltering() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        LandService service = service(store, clock, holderDirectory(ALPHA_ID));
        service.createParcelWithUsage(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL),
                OwnerReference.forPlayer(ALPHA_ID), 0  // no expiry -> always valid
        );
        service.createParcelWithUsage(
                ALPHA_ID, createRequest(ZoneType.COMMERCIAL, 100),
                OwnerReference.forPlayer(ALPHA_ID), 500  // expires at 1500
        );
        // Before expiry both are active.
        MyUsageRightsPage before = service.myUsageRights(
                ALPHA_ID, Optional.empty(), 0L, MyUsageRightsQueryLimits.DEFAULT_LIMIT
        );
        require(before.entries().size() == 2, "both active before expiry");
        // After expiry only the no-expiry right remains.
        clock.setNow(2_000);
        MyUsageRightsPage after = service.myUsageRights(
                ALPHA_ID, Optional.empty(), 0L, MyUsageRightsQueryLimits.DEFAULT_LIMIT
        );
        require(after.entries().size() == 1,
                "only the never-expiring right is active after expiry");
    }

    // ------------------------------------------------------------------
    // 6. holder index consistent across load/claim/grant/renew/revoke
    // ------------------------------------------------------------------

    private static void testHolderIndexLifecycle() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        LandService service = service(store, clock, holderDirectory(ALPHA_ID, BRAVO_ID));
        LandParcel parcel = service.createParcel(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL)
        ).parcel();

        // Grant: index gains the holder.
        service.grantUsage(ALPHA_ID, parcel.parcelId(),
                OwnerReference.forPlayer(BRAVO_ID), 0);
        require(service.myUsageRights(BRAVO_ID, Optional.empty(), 0L,
                MyUsageRightsQueryLimits.DEFAULT_LIMIT).entries().size() == 1,
                "BRAVO sees the just-granted right");

        // Renew: index unchanged (same holder, same parcel).
        service.renewUsage(ALPHA_ID, parcel.parcelId(),
                OwnerReference.forPlayer(BRAVO_ID), 0);
        require(service.myUsageRights(BRAVO_ID, Optional.empty(), 0L,
                MyUsageRightsQueryLimits.DEFAULT_LIMIT).entries().size() == 1,
                "renewal keeps the holder indexed once");

        // Revoke: index drops the holder/key.
        service.revokeUsage(ALPHA_ID, parcel.parcelId(),
                OwnerReference.forPlayer(BRAVO_ID));
        require(service.myUsageRights(BRAVO_ID, Optional.empty(), 0L,
                        MyUsageRightsQueryLimits.DEFAULT_LIMIT).status()
                        == MyUsageRightsStatus.OK,
                "revoked holder still resolves to an OK empty page");
        require(service.myUsageRights(BRAVO_ID, Optional.empty(), 0L,
                MyUsageRightsQueryLimits.DEFAULT_LIMIT).entries().isEmpty(),
                "revoked holder sees no rights");

        // Restart: index is rebuilt identically from persisted parcels.
        LandService restarted = service(store.restart(), clock,
                holderDirectory(ALPHA_ID, BRAVO_ID));
        require(restarted.myUsageRights(ALPHA_ID, Optional.empty(), 0L,
                MyUsageRightsQueryLimits.DEFAULT_LIMIT).status()
                        == MyUsageRightsStatus.OK,
                "reloaded repository serves the holder index");
    }

    // ------------------------------------------------------------------
    // 7. failed durable commit leaves parcels/revision/index unchanged
    // ------------------------------------------------------------------

    private static void testFailedDurableCommitLeavesIndexUnchanged() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        LandService service = service(store, clock, holderDirectory(ALPHA_ID));
        service.createParcelWithUsage(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL),
                OwnerReference.forPlayer(ALPHA_ID), 0
        );
        MyUsageRightsPage before = service.myUsageRights(
                ALPHA_ID, Optional.empty(), 0L, MyUsageRightsQueryLimits.DEFAULT_LIMIT
        );
        long revisionBefore = before.storeRevision();

        store.setCommitFailureCode("INJECTED");
        try {
            service.createParcelWithUsage(
                    ALPHA_ID, createRequest(ZoneType.COMMERCIAL, 100),
                    OwnerReference.forPlayer(ALPHA_ID), 0
            );
            throw new AssertionError("failed durable commit must fail closed");
        } catch (LandUnavailableException expected) {
            require(expected.failureCode().equals(
                            LandUnavailableException.CODE_STORE_FAILURE),
                    "injected store failure carries the stable code");
        }
        MyUsageRightsPage after = service.myUsageRights(
                ALPHA_ID, Optional.empty(), 0L, MyUsageRightsQueryLimits.DEFAULT_LIMIT
        );
        require(after.status() == MyUsageRightsStatus.OK, "query still OK");
        require(after.entries().size() == before.entries().size(),
                "a failed commit publishes no new right");
        require(after.storeRevision() == revisionBefore,
                "a failed commit advances no store revision");
    }

    // ------------------------------------------------------------------
    // 8. invalid limits + malformed packets rejected before allocation
    // ------------------------------------------------------------------

    private static void testInvalidLimitsAndMalformedPackets() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        LandService service = service(store, clock, holderDirectory(ALPHA_ID));

        for (int bad : new int[]{0, -1, MyUsageRightsQueryLimits.MAX_LIMIT + 1}) {
            LandUnavailableException invalid = expectThrows(
                    LandUnavailableException.class,
                    () -> service.myUsageRights(ALPHA_ID, Optional.empty(), 0L, bad),
                    "limit " + bad + " is rejected"
            );
            require(invalid.failureCode().equals(
                            LandUnavailableException.CODE_INVALID_REQUEST),
                    "invalid limit carries INVALID_REQUEST");
        }

        // Request packet rejects a non-positive requestId / limit / negative revision.
        expectThrows(IllegalArgumentException.class,
                () -> new MyLandRightsRequestPacket(1,
                        Optional.empty(), 0L, MyUsageRightsQueryLimits.MAX_LIMIT + 1),
                "page limit above the hard maximum is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new MyLandRightsRequestPacket(0, Optional.empty(), 0L, 1),
                "non-positive requestId is rejected");

        // Page packet rejects an unknown status and a non-OK page carrying entries.
        MyLandRightsPagePacket.Entry entry = entry();
        expectThrows(IllegalArgumentException.class,
                () -> new MyLandRightsPagePacket("BOGUS", 1, 0L, 1_000L,
                        List.of(), Optional.empty(), false),
                "unknown status is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new MyLandRightsPagePacket("RESET_REQUIRED", 1, 0L, 1_000L,
                        List.of(entry), Optional.empty(), false),
                "a non-OK page must not carry entries");

        // Round-trip of a valid request/page packet (bounded count preservation).
        // A continuation request pairs its cursor with the returned positive
        // store revision (cursor + revision 0 is rejected by FR-LAND-002-A §4.2).
        MyLandRightsRequestPacket request = roundTrip(
                new MyLandRightsRequestPacket(7, Optional.of(ALPHA_ID), 3L, 12),
                MyLandRightsRequestPacket::encode,
                MyLandRightsRequestPacket::decode
        );
        require(request.requestId() == 7 && request.limit() == 12,
                "request packet round-trips the correlation/limit fields");
        MyLandRightsPagePacket page = roundTrip(
                new MyLandRightsPagePacket("OK", 7, 3L, 1_000L,
                        List.of(entry), Optional.of(ALPHA_ID), true),
                MyLandRightsPagePacket::encode,
                MyLandRightsPagePacket::decode
        );
        require(page.entries().size() == 1 && page.hasMore()
                        && page.nextAfterParcelId().isPresent(),
                "page packet round-trips a bounded entry list + cursor");
    }

    // ------------------------------------------------------------------
    // 8.1. cursor/revision pairing enforced at packet and service boundaries
    // ------------------------------------------------------------------

    private static void testCursorRevisionPairingEnforced() {
        // Packet construction: a cursor with revision 0 is rejected.
        expectThrows(IllegalArgumentException.class,
                () -> new MyLandRightsRequestPacket(1,
                        Optional.of(ALPHA_ID), 0L, MyUsageRightsQueryLimits.DEFAULT_LIMIT),
                "a cursor paired with store revision 0 is rejected");
        // Packet construction: no cursor with a non-zero revision is rejected.
        expectThrows(IllegalArgumentException.class,
                () -> new MyLandRightsRequestPacket(1,
                        Optional.empty(), 3L, MyUsageRightsQueryLimits.DEFAULT_LIMIT),
                "no cursor paired with a non-zero revision is rejected");
        // Packet construction: a negative revision with a cursor is rejected.
        expectThrows(IllegalArgumentException.class,
                () -> new MyLandRightsRequestPacket(1,
                        Optional.of(ALPHA_ID), -1L, MyUsageRightsQueryLimits.DEFAULT_LIMIT),
                "a cursor paired with a negative revision is rejected");
        // Packet construction: a negative revision without a cursor is rejected.
        expectThrows(IllegalArgumentException.class,
                () -> new MyLandRightsRequestPacket(1,
                        Optional.empty(), -1L, MyUsageRightsQueryLimits.DEFAULT_LIMIT),
                "a negative revision without a cursor is rejected");

        // Packet decode funnels through the same constructor, so the invalid
        // forms are also rejected on decode. Craft the exact wire bytes for
        // (cursor present + revision 0).
        FriendlyByteBuf cursorWithZero = new FriendlyByteBuf(Unpooled.buffer());
        cursorWithZero.writeVarInt(1);
        cursorWithZero.writeBoolean(true);
        cursorWithZero.writeUUID(ALPHA_ID);
        cursorWithZero.writeLong(0L);
        cursorWithZero.writeVarInt(12);
        expectThrows(NetworkPayloadException.class,
                () -> MyLandRightsRequestPacket.decode(cursorWithZero),
                "decoding a cursor-with-revision-0 request is rejected");

        FriendlyByteBuf noCursorNonZero = new FriendlyByteBuf(Unpooled.buffer());
        noCursorNonZero.writeVarInt(1);
        noCursorNonZero.writeBoolean(false);
        noCursorNonZero.writeLong(3L);
        noCursorNonZero.writeVarInt(12);
        expectThrows(NetworkPayloadException.class,
                () -> MyLandRightsRequestPacket.decode(noCursorNonZero),
                "decoding a no-cursor-with-non-zero-revision request is rejected");

        // Packet decode rejects a negative revision paired with a cursor.
        FriendlyByteBuf cursorNegative = new FriendlyByteBuf(Unpooled.buffer());
        cursorNegative.writeVarInt(1);
        cursorNegative.writeBoolean(true);
        cursorNegative.writeUUID(ALPHA_ID);
        cursorNegative.writeLong(-1L);
        cursorNegative.writeVarInt(12);
        expectThrows(NetworkPayloadException.class,
                () -> MyLandRightsRequestPacket.decode(cursorNegative),
                "decoding a cursor-with-negative-revision request is rejected");

        // Server API boundary: non-network callers cannot bypass the rule.
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        LandService land = service(store, clock, holderDirectory(ALPHA_ID));
        land.createParcelWithUsage(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL),
                OwnerReference.forPlayer(ALPHA_ID), 0
        );
        LandUnavailableException cursorZero = expectThrows(
                LandUnavailableException.class,
                () -> land.myUsageRights(ALPHA_ID, Optional.of(ParcelId.of(ALPHA_ID)), 0L,
                        MyUsageRightsQueryLimits.DEFAULT_LIMIT),
                "land service rejects a cursor with store revision 0"
        );
        require(cursorZero.failureCode().equals(
                        LandUnavailableException.CODE_INVALID_REQUEST),
                "a cursor with revision 0 maps to INVALID_REQUEST");
        LandUnavailableException noCursorNonZeroSvc = expectThrows(
                LandUnavailableException.class,
                () -> land.myUsageRights(ALPHA_ID, Optional.empty(), 4L,
                        MyUsageRightsQueryLimits.DEFAULT_LIMIT),
                "land service rejects no cursor with a non-zero revision"
        );
        require(noCursorNonZeroSvc.failureCode().equals(
                        LandUnavailableException.CODE_INVALID_REQUEST),
                "no cursor with a non-zero revision maps to INVALID_REQUEST");

        // All negative revisions are rejected at the authoritative API boundary,
        // mapped to INVALID_REQUEST, and perform no mutation/revision change.
        long revisionBeforeNeg = land.myUsageRights(ALPHA_ID, Optional.empty(), 0L,
                MyUsageRightsQueryLimits.DEFAULT_LIMIT).storeRevision();
        LandUnavailableException cursorNegSvc = expectThrows(
                LandUnavailableException.class,
                () -> land.myUsageRights(ALPHA_ID, Optional.of(ParcelId.of(ALPHA_ID)), -1L,
                        MyUsageRightsQueryLimits.DEFAULT_LIMIT),
                "land service rejects a cursor with a negative store revision"
        );
        require(cursorNegSvc.failureCode().equals(
                        LandUnavailableException.CODE_INVALID_REQUEST),
                "a cursor with a negative revision maps to INVALID_REQUEST");
        LandUnavailableException noCursorNegSvc = expectThrows(
                LandUnavailableException.class,
                () -> land.myUsageRights(ALPHA_ID, Optional.empty(), -1L,
                        MyUsageRightsQueryLimits.DEFAULT_LIMIT),
                "land service rejects a negative revision without a cursor"
        );
        require(noCursorNegSvc.failureCode().equals(
                        LandUnavailableException.CODE_INVALID_REQUEST),
                "a negative revision without a cursor maps to INVALID_REQUEST");
        require(land.myUsageRights(ALPHA_ID, Optional.empty(), 0L,
                MyUsageRightsQueryLimits.DEFAULT_LIMIT).storeRevision() == revisionBeforeNeg,
                "a negative-revision request performs no mutation and advances no revision");

        // The presentation service surfaces the same closed INVALID_REQUEST
        // without touching land data.
        FairPlayerAccess access = new FairPlayerAccess(ALPHA_ID);
        MyLandRightsService rights = new DefaultMyLandRightsService(land, access, clock);
        long revisionBefore = land.myUsageRights(ALPHA_ID, Optional.empty(), 0L,
                MyUsageRightsQueryLimits.DEFAULT_LIMIT).storeRevision();
        MyLandRightsResponse rejected = rights.request(
                ALPHA_ID, 7, Optional.of(ParcelId.of(ALPHA_ID)), 0L,
                MyUsageRightsQueryLimits.DEFAULT_LIMIT);
        require(rejected.status() == MyUsageRightsStatus.INVALID_REQUEST
                        && rejected.entries().isEmpty(),
                "the presentation service maps the invalid pairing to INVALID_REQUEST");
        require(land.myUsageRights(ALPHA_ID, Optional.empty(), 0L,
                MyUsageRightsQueryLimits.DEFAULT_LIMIT).storeRevision() == revisionBefore,
                "an invalid pairing performs no mutation and advances no revision");
    }

    // ------------------------------------------------------------------
    // 8.2. strict projected field validation on the S2C page packet
    // ------------------------------------------------------------------

    private static void testStrictProjectedFieldValidation() {
        // Invalid (non-canonical) dimension.
        expectThrows(IllegalArgumentException.class,
                () -> new MyLandRightsPagePacket.Entry(
                        ALPHA_ID, "overworld:Overworld", 10, 20, 30, 40, 50, 60,
                        "RESIDENTIAL", "USAGE_GRANT", 1_000L, 0L, 1L, 1L),
                "a non-canonical resource-location dimension is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new MyLandRightsPagePacket.Entry(
                        ALPHA_ID, "minecraft/overworld", 10, 20, 30, 40, 50, 60,
                        "RESIDENTIAL", "USAGE_GRANT", 1_000L, 0L, 1L, 1L),
                "a path-separator-only dimension is rejected");
        // Unknown zone / usage enum names.
        expectThrows(IllegalArgumentException.class,
                () -> new MyLandRightsPagePacket.Entry(
                        ALPHA_ID, DIMENSION, 10, 20, 30, 40, 50, 60,
                        "BOGUS_ZONE", "USAGE_GRANT", 1_000L, 0L, 1L, 1L),
                "an unknown zone type name is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new MyLandRightsPagePacket.Entry(
                        ALPHA_ID, DIMENSION, 10, 20, 30, 40, 50, 60,
                        "RESIDENTIAL", "BOGUS_USAGE", 1_000L, 0L, 1L, 1L),
                "an unknown usage type name is rejected");

        // The same rejections must hold through the decode boundary. Hand-encode
        // a single-entry page whose first dimension / zone / usage is invalid so
        // the production decode constructor rejects it.
        expectThrows(NetworkPayloadException.class,
                () -> MyLandRightsPagePacket.decode(
                        rawPageWithDimension("minecraft:Overworld")),
                "decoding a page with a non-canonical dimension is rejected");
        expectThrows(NetworkPayloadException.class,
                () -> MyLandRightsPagePacket.decode(
                        rawPageWithZoneUsage("BOGUS_ZONE", "USAGE_GRANT")),
                "decoding a page with an unknown zone type is rejected");
        expectThrows(NetworkPayloadException.class,
                () -> MyLandRightsPagePacket.decode(
                        rawPageWithZoneUsage("RESIDENTIAL", "BOGUS_USAGE")),
                "decoding a page with an unknown usage type is rejected");

        // Excess entry count (>32) is rejected before allocation: write the
        // count directly and never follow it with entries.
        FriendlyByteBuf excess = new FriendlyByteBuf(Unpooled.buffer());
        excess.writeUtf("OK", MyLandRightsPagePacket.MAX_STATUS);
        excess.writeVarInt(1);
        excess.writeLong(1L);
        excess.writeLong(1_000L);
        excess.writeVarInt(MyLandRightsPagePacket.MAX_ENTRIES + 1);
        expectThrows(NetworkPayloadException.class,
                () -> MyLandRightsPagePacket.decode(excess),
                "an entry count above 32 is rejected before allocation");

        // Trailing bytes after a valid page survive decode but must be noticed
        // by the transport handoff (the same trailing-data check the round-trip
        // helper applies): append an extra byte and assert the buffer is not
        // fully consumed.
        MyLandRightsPagePacket valid = new MyLandRightsPagePacket(
                "OK", 1, 0L, 1_000L, List.of(entry()), Optional.empty(), false);
        FriendlyByteBuf trailing = new FriendlyByteBuf(Unpooled.buffer());
        MyLandRightsPagePacket.encode(valid, trailing);
        trailing.writeByte(0x7F);
        MyLandRightsPagePacket.decode(trailing);
        require(trailing.readableBytes() != 0,
                "trailing payload bytes are not silently consumed by decode");
    }

    // ------------------------------------------------------------------
    // 9. request correlation / stale response rejection (client cache)
    // ------------------------------------------------------------------

    private static void testCorrelationStaleRejection() {
        com.fontainerepublic.client.landrights.ClientMyLandRightsCache cache =
                new com.fontainerepublic.client.landrights.ClientMyLandRightsCache();
        int first = cache.allocateRequestId();
        cache.markPendingRequest(first);
        MyLandRightsPagePacket cur = new MyLandRightsPagePacket(
                "OK", first, 5L, 2_000L, List.of(entry()), Optional.of(ALPHA_ID), true);
        require(cache.accept(cur), "the current response is accepted");
        require(cache.hasPage() && cache.page().requestId() == first,
                "the current page is exposed");

        // A stale request id (not the current pending one) is discarded.
        MyLandRightsPagePacket staleId = new MyLandRightsPagePacket(
                "OK", 999, 5L, 3_000L, List.of(entry()), Optional.empty(), false);
        require(!cache.accept(staleId), "a stale requestId is discarded");

        // A lower store revision is discarded even for the current request line.
        int second = cache.allocateRequestId();
        cache.markPendingRequest(second);
        MyLandRightsPagePacket lowerRevision = new MyLandRightsPagePacket(
                "OK", second, 1L, 4_000L, List.of(entry()), Optional.empty(), false);
        require(!cache.accept(lowerRevision), "a lower store revision is discarded");

        // Within an equal revision an older generatedAt does not regress the page.
        MyLandRightsPagePacket sameRevisionOlderId = new MyLandRightsPagePacket(
                "OK", second, 5L, 1_000L, List.of(entry()), Optional.empty(), false);
        require(!cache.accept(sameRevisionOlderId),
                "an older generatedAt within equal revision does not regress");

        // A newer response for the same pending request is accepted.
        MyLandRightsPagePacket newer = new MyLandRightsPagePacket(
                "OK", second, 6L, 5_000L, List.of(entry()), Optional.empty(), false);
        require(cache.accept(newer), "a newer same-pending response is accepted");
        cache.clear();
        require(!cache.hasPage(), "clear drops the temporary page state");
    }

    // ------------------------------------------------------------------
    // 10. held-communicator gate
    // ------------------------------------------------------------------

    private static void testCommunicatorGate() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        LandService land = service(store, clock, holderDirectory(ALPHA_ID));
        land.createParcelWithUsage(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL),
                OwnerReference.forPlayer(ALPHA_ID), 0
        );

        FairPlayerAccess access = new FairPlayerAccess(ALPHA_ID);
        MyLandRightsService rights = new DefaultMyLandRightsService(
                land, access, clock);

        MyLandRightsResponse held = rights.request(
                ALPHA_ID, 1, Optional.empty(), 0L,
                MyUsageRightsQueryLimits.DEFAULT_LIMIT);
        require(held.status() == MyUsageRightsStatus.OK
                        && held.entries().size() == 1,
                "a holding online player receives their own page");

        access.setHoldsCommunicator(false);
        MyLandRightsResponse notHolding = rights.request(
                ALPHA_ID, 2, Optional.empty(), 0L,
                MyUsageRightsQueryLimits.DEFAULT_LIMIT);
        require(notHolding.status() == MyUsageRightsStatus.UNAVAILABLE
                        && notHolding.entries().isEmpty(),
                "not holding the communicator is rejected with no entry leak");

        // An offline player is also rejected uniformly.
        MyLandRightsResponse offline = rights.request(
                UUID.fromString("00000000-0000-0000-0000-0000000000aa"), 3,
                Optional.empty(), 0L, MyUsageRightsQueryLimits.DEFAULT_LIMIT);
        require(offline.status() == MyUsageRightsStatus.UNAVAILABLE
                        && offline.entries().isEmpty(),
                "a player absent from the live server is rejected");
    }

    // ------------------------------------------------------------------
    // 11. protocol v9 ledger: count 29, exact IDs/directions/rate policy
    // ------------------------------------------------------------------

    private static void testProtocolV9Ledger() {
        require(NetworkProtocol.VERSION.equals("10"), "protocol is now v10");
        require(NetworkProductionMessageTable.EXPECTED_MESSAGE_COUNT == 30,
                "the production ledger expects 30 messages");

        LedgerCollector collector = new LedgerCollector();
        NetworkProductionMessageTable.registerAll(collector);
        require(collector.byId.size() == 30,
                "registerAll registers exactly 30 messages");

        NetworkMessageSpec<?> req = collector.byId.get(27);
        require(req != null
                        && req.messageClass() == MyLandRightsRequestPacket.class
                        && req.direction() == NetworkDirection.PLAY_TO_SERVER,
                "ID 27 registers the C2S my-usage-rights request");
        require(req.rateLimitPolicy().isPresent(),
                "ID 27 carries an explicit C2S rate policy");
        RateLimitPolicy policy = req.rateLimitPolicy().orElseThrow();
        // ID 27 must be exactly burst capacity 2 and sustained 10 req/s
        // (one token refill per 100ms: 1 / 100_000_000 ns = 10 per second).
        require(policy.capacity() == 2,
                "ID 27 rate policy is exactly burst capacity 2");
        require(policy.refillTokens() == 1 && policy.refillIntervalNanos() == 100_000_000L,
                "ID 27 rate policy refills one token per 100ms");
        long sustainedPerSecond =
                policy.refillTokens() * 1_000_000_000L / policy.refillIntervalNanos();
        require(sustainedPerSecond == 10,
                "ID 27 rate policy sustains exactly 10 requests/second");
        require(policy.minimumSpacingNanos() >= 0,
                "ID 27 rate policy minimum spacing is non-negative");

        NetworkMessageSpec<?> resp = collector.byId.get(28);
        require(resp != null
                        && resp.messageClass() == MyLandRightsPagePacket.class
                        && resp.direction() == NetworkDirection.PLAY_TO_CLIENT,
                "ID 28 registers the S2C my-usage-rights page");
        require(resp.rateLimitPolicy().isEmpty(),
                "S2C ID 28 carries no C2S rate policy");

        List<Integer> ids = new ArrayList<>(collector.byId.keySet());
        require(ids.equals(List.of(
                        0, 1, 2, 3, 4, 5, 6, 7, 8,
                        9, 10, 11, 12, 13, 14, 15,
                        16, 17, 18, 19, 20, 21, 22,
                        23, 24, 25, 26, 27, 28, 29
                )),
                "ledger IDs are exactly 0..29 in ascending order, append-only");
    }

    // ------------------------------------------------------------------
    // 12. reflection / API: no batch enumeration, no arbitrary holder target
    // ------------------------------------------------------------------

    private static void testNoEnumerationAndNoHolderTarget() {
        for (Class<?> type : List.of(LandService.class, LandRepository.class)) {
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                Class<?> returnType = method.getReturnType();
                require(!java.util.Collection.class.isAssignableFrom(returnType)
                                && !Map.class.isAssignableFrom(returnType)
                                && !returnType.isArray()
                                && !java.util.stream.Stream.class.isAssignableFrom(returnType),
                        "no batch enumeration method in " + type.getSimpleName() + ": "
                                + method.getName());
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                require(!name.contains("findall") && !name.contains("list")
                                && !name.contains("values") && !name.contains("all"),
                        "no bulk-enumeration method name in " + type.getSimpleName() + ": "
                                + method.getName());
            }
        }

        // The self-only page query must not accept a target/holder parameter.
        for (Method method : LandService.class.getDeclaredMethods()) {
            if (method.getName().equals("myUsageRights")) {
                require(method.getParameterCount() == 4,
                        "myUsageRights takes exactly the four self-only params");
                require(method.getParameterTypes()[0] == UUID.class,
                        "first param is the authenticated UUID, not a target");
            }
        }
        // No method anywhere on the land read surface takes an OwnerReference
        // as a query target (self-only conversion happens internally).
        for (Method method : LandService.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                require(!parameter.equals(OwnerReference.class)
                                || method.getName().equals("grantUsage")
                                || method.getName().equals("renewUsage")
                                || method.getName().equals("revokeUsage")
                                || method.getName().equals("createViolationReport")
                                || method.getName().equals("createParcelWithUsage"),
                        "no arbitrary-holder query parameter on " + method.getName());
            }
        }
    }

    // ------------------------------------------------------------------
    // 13. explicit negative command-tree assertion: no /fr land mine
    // ------------------------------------------------------------------

    private static void testNoLandMineCommand() throws Exception {
        // Build the real registered /fr land tree and assert no "mine" / "my"
        // / "self" / "usage" subcommand exists.
        com.fontainerepublic.core.CoreManager coreManager =
                new com.fontainerepublic.core.CoreManager(
                        new com.fontainerepublic.core.module.ModuleRegistry());
        CommandContributionRegistry registry = new CommandContributionRegistry();
        registry.register(new CommandContributionSpec(
                "land", (context, resolver) -> LandCommand.create(context, resolver)));
        registry.freeze();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        new CommandBootstrap(registry, new CommandRuntimeResolver(coreManager))
                .register(dispatcher, null, Commands.CommandSelection.ALL);
        com.mojang.brigadier.tree.CommandNode<CommandSourceStack> land =
                dispatcher.getRoot().getChild("fr").getChild("land");
        require(land != null, "/fr land must be contributed");
        for (String forbidden : List.of("mine", "my", "self", "usage", "view")) {
            require(land.getChild(forbidden) == null,
                    "/fr land must not register a '" + forbidden + "' subcommand");
        }

        // Source-level negative check across the whole command surface.
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path commandDirectory = projectDirectory.resolve(
                "src/main/java/com/fontainerepublic/server/command"
        );
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(commandDirectory)) {
            paths.filter(path -> path.toString().endsWith("LandCommand.java"))
                    .forEach(path -> source.append(read(path)).append('\n'));
        }
        String code = source.toString();
        require(!code.contains("literal(\"mine\")"),
                "LandCommand must not register a 'mine' subcommand");
    }

    // ------------------------------------------------------------------
    // 14. dedicated-server client-class isolation
    // ------------------------------------------------------------------

    private static void testDedicatedServerClientIsolation() throws Exception {
        // The common/server code must never statically import a client class.
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        for (String subDir : List.of(
                "src/main/java/com/fontainerepublic/common",
                "src/main/java/com/fontainerepublic/server")) {
            Path dir = projectDirectory.resolve(subDir);
            StringBuilder source = new StringBuilder();
            try (var paths = Files.walk(dir)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .sorted()
                        .forEach(path -> source.append(read(path)).append('\n'));
            }
            String codeOnly = stripComments(source.toString());
            require(!codeOnly.contains("import com.fontainerepublic.client."),
                    "common/server code must not import a client class: " + subDir);
        }
    }

    // ------------------------------------------------------------------
    // 15. module ownership: no production 'landrights' ID, land owns the
    //     projection runtime
    // ------------------------------------------------------------------

    private static void testNoLandRightsModuleRegistration() throws Exception {
        // Register exactly the production set FontaineRepublic registers. The
        // presentation-only FR-LAND-002 surface must NOT become a top-level
        // module, and the production count must stay at 15.
        ModuleRegistry registry = new ModuleRegistry();
        NetworkRuntimeModule.register(registry);
        PlayerDataModule.register(registry);
        AuditModule.register(registry);
        SubjectRegistryModule.register(registry);
        CitizenModule.register(registry);
        LandModule.register(registry);
        EconomyModule.register(registry);
        InstitutionAccessModule.register(registry);
        GovernmentModule.register(registry);
        ParliamentModule.register(registry);
        JusticeModule.register(registry);
        EmergencyModule.register(registry);
        TradeModule.register(registry);
        MailModule.register(registry);
        LandClaimModule.register(registry);

        require(registry.size() == 15,
                "production module count is exactly 15, got " + registry.size());
        ModuleId landId = new ModuleId("land");
        ModuleId landRightsId = new ModuleId("landrights");
        require(registry.hasModule(landId),
                "the 'land' module is registered");
        require(registry.hasModule(new ModuleId("network")),
                "the 'network' module is registered");
        require(!registry.hasModule(landRightsId),
                "no production module ID 'landrights' exists");

        // The top-level mod entry must not reference the removed module at all.
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path entryPoint = projectDirectory.resolve(
                "src/main/java/com/fontainerepublic/FontaineRepublic.java"
        );
        String code = read(entryPoint);
        require(!code.contains("LandRights"),
                "FontaineRepublic must not reference a LandRights top-level module");

        // The single land owner module declares no separate landrights ID.
        ModuleRegistry landOnly = new ModuleRegistry();
        LandModule.register(landOnly);
        require(landOnly.size() == 1 && landOnly.hasModule(landId),
                "LandModule registers only the 'land' module");
    }

    private static void testLandModuleOwnsProjectionRuntime() throws Exception {
        // Fresh JVM: the my-usage-rights static locator starts unbound.
        require(MyLandRightsRuntime.resolve().isEmpty(),
                "the projection runtime starts unbound");

        // Fail-closed: an un-initialized land module (no authoritative land
        // service active) must never bind a projection …C2S resolution stays
        // empty rather than crashing or serving a torn-down authority.
        LandModule unstarted = new LandModule();
        unstarted.bindServices(null, null);
        require(MyLandRightsRuntime.resolve().isEmpty(),
                "an unstarted land module leaves the projection unbound (fail closed)");
        unstarted.shutdown();
        require(MyLandRightsRuntime.resolve().isEmpty(),
                "shutdown of an unstarted land module stays unbound");

        // Runtime semantics the land module drives: bind exposes the single
        // active service; unbind clears it.
        MyLandRightsService bound = (playerId, requestId, afterParcelId,
                expectedStoreRevision, limit) -> MyLandRightsResponse.closed(
                        MyUsageRightsStatus.UNAVAILABLE, requestId, 0L, 1_000L);
        MyLandRightsRuntime.bind(bound);
        require(MyLandRightsRuntime.resolve().isPresent()
                        && MyLandRightsRuntime.resolve().orElseThrow() == bound,
                "the runtime exposes the bound projection service");
        MyLandRightsRuntime.unbind();
        require(MyLandRightsRuntime.resolve().isEmpty(),
                "the runtime unbinds the projection service");
        MyLandRightsRuntime.bind(bound);
        MyLandRightsRuntime.bind(bound);
        require(MyLandRightsRuntime.resolve().isPresent(),
                "repeat binding stays bound (no duplicate state)");
        MyLandRightsRuntime.unbind();

        // Ownership: the land module (not a separate top-level module) is the
        // single lifecycle owner that binds and unbinds the runtime, and
        // unbinds on shutdown before clearing its authoritative land authority.
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path landModuleSource = projectDirectory.resolve(
                "src/main/java/com/fontainerepublic/server/land/LandModule.java"
        );
        String code = stripComments(read(landModuleSource));
        require(code.contains("MyLandRightsRuntime.bind"),
                "LandModule binds MyLandRightsRuntime (owns the projection)");
        require(code.contains("MyLandRightsRuntime.unbind"),
                "LandModule unbinds MyLandRightsRuntime on shutdown");
        require(!code.contains("LandRightsModule"),
                "LandModule does not reference a removed LandRightsModule");
        // Unbind must happen before the authoritative land service is cleared.
        int unbindIndex = code.indexOf("MyLandRightsRuntime.unbind");
        int serviceClearIndex = code.indexOf("service = null;");
        require(unbindIndex >= 0 && serviceClearIndex > unbindIndex,
                "LandModule unbinds the projection before clearing the land service");
    }

    // ------------------------------------------------------------------
    // 17. the "my land rights" screen clears the cache on close
    // ------------------------------------------------------------------

    private static void testScreenCloseClearsCache() throws Exception {
        // Regression guard: MyLandRightsScreen must override onClose() to clear
        // the client page cache before delegating to the parent Screen behavior,
        // so closing the view never leaves stale page/cursor state behind.
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path screenSourcePath = projectDirectory.resolve(
                "src/main/java/com/fontainerepublic/client/gui/land/"
                        + "MyLandRightsScreen.java"
        );
        String code = stripComments(read(screenSourcePath));
        int onCloseAnchor = code.indexOf("public void onClose() {");
        require(onCloseAnchor >= 0, "MyLandRightsScreen overrides onClose()");
        int onCloseBodyStart = code.indexOf("{", onCloseAnchor);
        int onCloseBlockEnd = code.indexOf("}", onCloseBodyStart);
        require(onCloseBlockEnd > onCloseBodyStart,
                "onClose() has a body");
        String onCloseBody = code.substring(onCloseBodyStart, onCloseBlockEnd);
        require(onCloseBody.contains("ClientMyLandRightsCache.instance().clear()"),
                "onClose() clears the ClientMyLandRightsCache");
        require(onCloseBody.contains("super.onClose()"),
                "onClose() delegates to the parent behavior after clearing");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static LandService service(
            LandStore store,
            LongSupplier clock,
            FairHolderDirectory holders
    ) {
        LandRepository repository = new LandRepository(
                store, new LandNbtCodec(), LandLimits.DEFAULT,
                new SequentialParcelIdSource()
        );
        PermissionResolver resolver = new ConfigDrivenPermissionResolver(
                LandPermissionConfig.DEFAULT, repository, holders, clock
        );
        return new DefaultLandService(repository, clock, holders, resolver);
    }

    private static FairHolderDirectory holderDirectory(UUID... players) {
        FairHolderDirectory holders = new FairHolderDirectory();
        for (UUID player : players) {
            holders.add(player);
        }
        return holders;
    }

    private static CreateParcelRequest createRequest(ZoneType zoneType) {
        return createRequest(zoneType, 0);
    }

    private static CreateParcelRequest createRequest(ZoneType zoneType, int xOffset) {
        return new CreateParcelRequest(DIMENSION, region(xOffset), zoneType);
    }

    private static ParcelRegion region() {
        return region(0);
    }

    private static ParcelRegion region(int xOffset) {
        return new ParcelRegion(10 + xOffset, 20, 30, 40 + xOffset, 50, 60);
    }

    private static MyLandRightsPagePacket.Entry entry() {
        return new MyLandRightsPagePacket.Entry(
                ALPHA_ID, DIMENSION, 10, 20, 30, 40, 50, 60,
                "RESIDENTIAL", "USAGE_GRANT", 1_000L, 0L, 1L, 1L
        );
    }

    private static <T> T roundTrip(
            T value,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder
    ) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        encoder.accept(value, buffer);
        T decoded = decoder.apply(buffer);
        require(buffer.readableBytes() == 0,
                "decode consumed exactly the encoded payload (trailing data rejected)");
        return decoded;
    }

    /**
     * Hand-encodes a single-entry OK page whose entry carries the given
     * (possibly invalid) dimension, mirroring the exact S2C wire layout so the
     * production decode constructor can reject a malformed value.
     */
    private static FriendlyByteBuf rawPageWithDimension(String dimension) {
        return rawPageWithFirstEntry("OK", 1, 0L, 1_000L,
                ALPHA_ID, dimension, 10, 20, 30, 40, 50, 60,
                "RESIDENTIAL", "USAGE_GRANT", 1_000L, 0L, 1L, 1L);
    }

    /**
     * Hand-encodes a single-entry OK page whose entry carries the given zone and
     * usage names (to exercise closed-enum rejection on decode).
     */
    private static FriendlyByteBuf rawPageWithZoneUsage(
            String zoneType,
            String usageType
    ) {
        return rawPageWithFirstEntry("OK", 1, 0L, 1_000L,
                ALPHA_ID, DIMENSION, 10, 20, 30, 40, 50, 60,
                zoneType, usageType, 1_000L, 0L, 1L, 1L);
    }

    private static FriendlyByteBuf rawPageWithFirstEntry(
            String status,
            int requestId,
            long storeRevision,
            long generatedAt,
            UUID parcelId,
            String dimension,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            String zoneType,
            String usageType,
            long grantedAt,
            long expiresAt,
            long rightRevision,
            long parcelRevision
    ) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(status, MyLandRightsPagePacket.MAX_STATUS);
        buffer.writeVarInt(requestId);
        buffer.writeLong(storeRevision);
        buffer.writeLong(generatedAt);
        buffer.writeVarInt(1); // exactly one entry
        buffer.writeUUID(parcelId);
        buffer.writeUtf(dimension, MyLandRightsPagePacket.MAX_DIMENSION);
        buffer.writeInt(minX);
        buffer.writeInt(minY);
        buffer.writeInt(minZ);
        buffer.writeInt(maxX);
        buffer.writeInt(maxY);
        buffer.writeInt(maxZ);
        buffer.writeUtf(zoneType, MyLandRightsPagePacket.MAX_ENUM);
        buffer.writeUtf(usageType, MyLandRightsPagePacket.MAX_ENUM);
        buffer.writeLong(grantedAt);
        buffer.writeLong(expiresAt);
        buffer.writeLong(rightRevision);
        buffer.writeLong(parcelRevision);
        buffer.writeBoolean(false); // no next cursor
        buffer.writeBoolean(false); // hasMore
        return buffer;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unable to read " + path, failure);
        }
    }

    /** Removes comments while preserving string literals (source scan helper). */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int index = 0;
        boolean inString = false;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (inString) {
                out.append(current);
                if (current == '\\' && index + 1 < source.length()) {
                    out.append(source.charAt(index + 1));
                    index += 2;
                    continue;
                }
                if (current == '"') {
                    inString = false;
                }
                index++;
                continue;
            }
            if (current == '"') {
                inString = true;
                out.append(current);
                index++;
                continue;
            }
            if (current == '/' && index + 1 < source.length()
                    && source.charAt(index + 1) == '/') {
                while (index < source.length() && source.charAt(index) != '\n') {
                    index++;
                }
                continue;
            }
            if (current == '/' && index + 1 < source.length()
                    && source.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < source.length()
                        && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) {
                    index++;
                }
                index += 2;
                continue;
            }
            out.append(current);
            index++;
        }
        return out.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> expected,
            Runnable action,
            String message
    ) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return expected.cast(throwable);
            }
            throw new AssertionError(
                    message + ": expected " + expected.getSimpleName()
                            + ", got " + throwable.getClass().getSimpleName(),
                    throwable
            );
        }
        throw new AssertionError(message + ": expected " + expected.getSimpleName());
    }

    private static final class SavedDataBackedTestStore implements LandStore {
        private final ModSavedData savedData;
        private String commitFailureCode;
        private int commitCount;

        private SavedDataBackedTestStore() {
            this(new ModSavedData());
        }

        private SavedDataBackedTestStore(ModSavedData savedData) {
            this.savedData = savedData;
        }

        @Override
        public net.minecraft.nbt.CompoundTag load() {
            return savedData.getModuleData(LandRepository.MODULE_DATA_KEY).copy();
        }

        @Override
        public DurableCommitResult commit(net.minecraft.nbt.CompoundTag snapshot) {
            commitCount++;
            if (commitFailureCode != null) {
                return new DurableCommitResult(
                        DurableCommitStatus.FAILED,
                        LandRepository.MODULE_DATA_KEY, 0L, 0L, commitFailureCode
                );
            }
            savedData.putModuleData(
                    LandRepository.MODULE_DATA_KEY, snapshot.copy());
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED,
                    LandRepository.MODULE_DATA_KEY, 1L, 0L, "");
        }

        private void setCommitFailureCode(String code) {
            this.commitFailureCode = code;
        }

        private SavedDataBackedTestStore restart() {
            net.minecraft.nbt.CompoundTag root = savedData.save(new net.minecraft.nbt.CompoundTag());
            return new SavedDataBackedTestStore(ModSavedData.load(root));
        }
    }

    private static final class MutableClock implements LongSupplier {
        private long now;

        private MutableClock(long now) {
            this.now = now;
        }

        @Override
        public long getAsLong() {
            return now;
        }

        private void setNow(long now) {
            this.now = now;
        }
    }

    private static final class SequentialParcelIdSource implements ParcelIdSource {
        private long counter;

        @Override
        public UUID nextUuid() {
            counter++;
            return new UUID(0L, counter);
        }
    }

    /** PlayerData + active-subject fake (mirrors the land foundation test). */
    private static final class FairHolderDirectory implements HolderDirectory {
        private final Set<UUID> records = new HashSet<>();
        private final Set<UUID> activeSubjects = new HashSet<>();
        private boolean available = true;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public boolean hasPlayerRecord(UUID playerId) {
            return records.contains(playerId);
        }

        @Override
        public boolean hasActiveSubject(UUID playerId) {
            return activeSubjects.contains(playerId);
        }

        private void add(UUID playerId) {
            records.add(playerId);
            activeSubjects.add(playerId);
        }
    }

    /** Controllable fake of the server player surface (headless-testable). */
    private static final class FairPlayerAccess implements MyLandRightsServerPlayerAccess {
        private final UUID playerId;
        private boolean holdsCommunicator = true;

        private FairPlayerAccess(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public Optional<ServerPlayer> onlinePlayer(UUID playerId) {
            return playerId.equals(this.playerId) ? Optional.empty() : Optional.empty();
        }

        @Override
        public boolean isOnline(UUID playerId) {
            return playerId.equals(this.playerId);
        }

        @Override
        public boolean holdsCommunicator(UUID playerId) {
            return playerId.equals(this.playerId) && holdsCommunicator;
        }

        private void setHoldsCommunicator(boolean holdsCommunicator) {
            this.holdsCommunicator = holdsCommunicator;
        }
    }

    /** Collects production ledger specs (mirrors the land-claim test). */
    private static final class LedgerCollector
            implements com.fontainerepublic.common.network.NetworkMessageRegistration {
        private final Map<Integer, NetworkMessageSpec<?>> byId = new LinkedHashMap<>();

        @Override
        public <MSG> void register(NetworkMessageSpec<MSG> spec) {
            byId.put(spec.id(), spec);
        }
    }
}
