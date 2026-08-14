package com.fontainerepublic.server.landclaim;

import com.fontainerepublic.common.landclaim.LandClaimPacket;
import com.fontainerepublic.common.landclaim.LandClaimResultPacket;
import com.fontainerepublic.common.landclaim.LandInspectPacket;
import com.fontainerepublic.common.landclaim.LandInspectResultPacket;
import net.minecraftforge.network.NetworkDirection;
import com.fontainerepublic.common.network.NetworkMessageRegistrar;
import com.fontainerepublic.common.network.NetworkMessageSpec;
import com.fontainerepublic.common.network.NetworkMessageRegistration;
import com.fontainerepublic.common.network.NetworkProductionMessageTable;
import com.fontainerepublic.common.network.NetworkProtocol;
import com.fontainerepublic.common.network.RateLimitPolicy;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.core.ModSavedData;
import com.fontainerepublic.server.land.api.CreateParcelRequest;
import com.fontainerepublic.server.land.api.HolderDirectory;
import com.fontainerepublic.server.land.api.LandService;
import com.fontainerepublic.server.land.api.PermissionResolver;
import com.fontainerepublic.server.land.api.UsageReceipt;
import com.fontainerepublic.server.land.model.LandAccess;
import com.fontainerepublic.server.land.model.LandOwnership;
import com.fontainerepublic.server.land.model.LandParcel;
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
import com.fontainerepublic.server.landclaim.api.ClaimReceipt;
import com.fontainerepublic.server.landclaim.api.InspectResult;
import com.fontainerepublic.server.landclaim.api.LandClaimService;
import com.fontainerepublic.server.landclaim.api.ServerLandClaimPlayerAccess;
import com.fontainerepublic.server.landclaim.service.DefaultLandClaimService;
import com.fontainerepublic.server.command.LandCommand;
import com.fontainerepublic.server.registry.model.OwnerReference;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Dependency-free validation entry point for FR-LAND-CLAIM-001 (communicator
 * land claim). Exercises the bounded single-point query and the atomic
 * {@code createParcelWithUsage} single-snapshot durable commit, the claim and
 * inspect gates (online / communicator / dimension / loading / reach), the
 * exact-point and full-region rejection, the store-failure atomicity with
 * retry, the four packet codecs and their bounds, the production ledger rate
 * policies, and a source-level scan that the no-client command shares the very
 * same service (no OP or entry-point bypass).
 */
public final class LandClaimFoundationTestMain {

    private static final String DIMENSION = "minecraft:overworld";
    private static final UUID ALPHA_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRAVO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private LandClaimFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testExactPointQuery();
        testSingleSnapshotAtomicCreate();
        testAlreadyOwnedRejected();
        testOverlapRejected();
        testGatesFailClosed();
        testNoSubjectFailsClosed();
        testStoreFailureAtomicAndRetryable();
        testNoExpiryRightOnSuccess();
        testPacketCodecRoundTrip();
        testPacketValueBounds();
        testRatePoliciesRegistered();
        testNoClientParityAndNoOpBypass();
        testCommandFeedbackAndSharedService();
        testModuleContract();
        testUnavailableLandServiceFailsClosed();
        System.out.println("[FR-LAND-CLAIM-001] Land claim foundation validation passed");
    }

    // ------------------------------------------------------------------
    // bounded exact single-point query
    // ------------------------------------------------------------------

    private static void testExactPointQuery() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        LandRepository repository = repository(store);
        LandService land = service(repository, clock, holders(ALPHA_ID));
        DefaultLandClaimService claim = claimService(land, fakeAccess(ALPHA_ID), clock);

        // Before any parcel: empty for every point.
        require(land.parcelAt(DIMENSION, 0, 60, 0).isEmpty(),
                "an empty store yields an empty point query");
        require(claim.inspect(ALPHA_ID, DIMENSION, 0, 60, 0).claimable(),
                "an unowned point inspects as claimable");

        // After creating a parcel covering 10..40 / 20..50 / 30..60.
        land.createParcel(ALPHA_ID, new CreateParcelRequest(
                DIMENSION, region(), ZoneType.RESIDENTIAL, LandAccess.PUBLIC));
        Optional<LandParcel> inside = land.parcelAt(DIMENSION, 11, 21, 31);
        require(inside.isPresent(), "a parcel covers the queried point");
        Optional<LandParcel> outside = land.parcelAt(DIMENSION, 999, 999, 999);
        require(outside.isEmpty(), "a point outside every parcel is empty");
        Optional<LandParcel> wrongDim = land.parcelAt("minecraft:the_nether", 11, 21, 31);
        require(wrongDim.isEmpty(), "a different dimension never matches");
        require(claim.inspect(ALPHA_ID, DIMENSION, 11, 21, 31).parcelId() != null,
                "inspect reports the covering parcel id");
        require(claim.inspect(ALPHA_ID, DIMENSION, 999, 60, 999).claimable(),
                "inspect of an unowned far point with a clear default region is claimable");
        require(!claim.inspect(ALPHA_ID, DIMENSION, 999, 999, 999).claimable()
                        && InspectResult.CODE_INVALID.equals(
                        claim.inspect(ALPHA_ID, DIMENSION, 999, 999, 999).code()),
                "inspect of a point outside the world build range is deniable (INVALID)");
    }

    // ------------------------------------------------------------------
    // single snapshot, atomic create + initial usage right
    // ------------------------------------------------------------------

    private static void testSingleSnapshotAtomicCreate() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(2_000);
        LandRepository repository = repository(store);
        LandService land = service(repository, clock, holders(ALPHA_ID));
        DefaultLandClaimService claim = claimService(land, fakeAccess(ALPHA_ID), clock);

        int commitsBefore = store.commitCount();
        ClaimReceipt receipt = claim.claim(ALPHA_ID, DIMENSION, 0, 60, 0);
        require(receipt.success() && receipt.code().equals(ClaimReceipt.CODE_OK),
                "a claim on unowned land succeeds");
        require(receipt.parcelId() != null, "the receipt carries the parcel id");

        LandParcel parcel = land.getParcel(
                com.fontainerepublic.server.land.model.ParcelId.of(receipt.parcelId()))
                .orElseThrow();
        require(parcel.ownership() == LandOwnership.REPUBLIC,
                "the claimed parcel is republic-owned");
        require(parcel.parcelRevision() == 1L, "the claimed parcel starts at revision 1");
        require(parcel.usageRightOf(OwnerReference.forPlayer(ALPHA_ID)).isPresent(),
                "the claimant receives the initial usage right");
        require(parcel.usageRightOf(OwnerReference.forPlayer(ALPHA_ID))
                        .orElseThrow().rightRevision() == 1L,
                "the initial usage right starts at revision 1");
        require(parcel.zoneType() == ZoneType.RESIDENTIAL,
                "the default zone type is RESIDENTIAL");
        require(parcel.access() == LandAccess.PRIVATE,
                "the default access is PRIVATE");
        require(repository.snapshot().storeRevision() == 1L,
                "one successful claim advances the store revision to 1");
        require(store.commitCount() == commitsBefore + 1,
                "one successful claim commits exactly one snapshot (create + grant atomic)");
    }

    private static void testNoExpiryRightOnSuccess() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(3_000);
        LandRepository repository = repository(store);
        LandService land = service(repository, clock, holders(ALPHA_ID));
        DefaultLandClaimService claim = claimService(land, fakeAccess(ALPHA_ID), clock);

        claim.claim(ALPHA_ID, DIMENSION, 0, 60, 0);
        LandParcel parcel = repository.snapshot().parcels().values().iterator().next();
        long expiresAt = parcel.usageRightOf(OwnerReference.forPlayer(ALPHA_ID))
                .orElseThrow().expiresAt();
        require(expiresAt == 0L, "the claim grants a non-expiring usage right");
    }

    // ------------------------------------------------------------------
    // exact-point already-owned rejection (fail closed, no side effect)
    // ------------------------------------------------------------------

    private static void testAlreadyOwnedRejected() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(4_000);
        LandRepository repository = repository(store);
        LandService land = service(repository, clock, holders(ALPHA_ID));
        DefaultLandClaimService claim = claimService(land, fakeAccess(ALPHA_ID), clock);
        land.createParcel(ALPHA_ID, new CreateParcelRequest(
                DIMENSION, region(), ZoneType.RESIDENTIAL, LandAccess.PUBLIC));
        int commitsBefore = store.commitCount();

        ClaimReceipt receipt = claim.claim(ALPHA_ID, DIMENSION, 11, 21, 31);
        require(!receipt.success()
                        && receipt.code().equals(ClaimReceipt.CODE_ALREADY_OWNED),
                "a point already covered by a parcel is rejected with ALREADY_OWNED");
        require(store.commitCount() == commitsBefore,
                "an already-owned rejection commits nothing");
        require(repository.snapshot().storeRevision() == 1L,
                "an already-owned rejection advances no revision");
        InspectResult inspected = claim.inspect(ALPHA_ID, DIMENSION, 11, 21, 31);
        require(!inspected.claimable() && inspected.parcelId() != null,
                "inspect of an owned point reports the parcel");
    }

    // ------------------------------------------------------------------
    // full-region overlap rejection (fail closed, no side effect)
    // ------------------------------------------------------------------

    private static void testOverlapRejected() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(5_000);
        LandRepository repository = repository(store);
        LandService land = service(repository, clock, holders(ALPHA_ID));
        // halfWidth=1 height=8 -> region x[-1,1] y[60,67] z[-1,1] around (0,60,0).
        DefaultLandClaimService claim = new DefaultLandClaimService(
                land, fakeAccess(ALPHA_ID), clock, 1, 8, "RESIDENTIAL");

        // First claim occupies x[-1,1] z[-1,1].
        claim.claim(ALPHA_ID, DIMENSION, 0, 60, 0);
        int commitsBefore = store.commitCount();

        // A point NOT already owned (2,60,2) but whose default region x[1,3]
        // z[1,3] overlaps the existing x[-1,1] z[-1,1] parcel at the shared
        // corner (1,1).
        ClaimReceipt receipt = claim.claim(ALPHA_ID, DIMENSION, 2, 60, 2);
        require(!receipt.success() && receipt.code().equals(ClaimReceipt.CODE_OVERLAP),
                "a full-region overlap with an existing parcel is rejected with OVERLAP");
        require(store.commitCount() == commitsBefore,
                "an overlap rejection commits nothing");
        require(repository.snapshot().storeRevision() == 1L,
                "an overlap rejection advances no revision");
        require(repository.size() == 1, "an overlap rejection creates no parcel");

        // FR-LAND-CLAIM-001-FIX-01 F2: the inspect promise must be truthful for
        // an unowned click point whose planned default region still overlaps an
        // adjacent parcel — it must report a deniable overlap, never claimable.
        InspectResult adjacent = claim.inspect(ALPHA_ID, DIMENSION, 2, 60, 2);
        require(!adjacent.claimable()
                        && InspectResult.CODE_OVERLAP.equals(adjacent.code()),
                "an inspect whose default region overlaps an adjacent parcel "
                        + "must be deniable (code " + adjacent.code() + ")");

        // A point whose planned region is entirely clear stays claimable.
        InspectResult clear = claim.inspect(ALPHA_ID, DIMENSION, 5, 60, 5);
        require(clear.claimable(),
                "an inspect with a clear default region remains claimable");
    }

    // ------------------------------------------------------------------
    // gates fail closed (online / holding / dimension / loading / reach)
    // ------------------------------------------------------------------

    private static void testGatesFailClosed() {
        LandRepository repository = repository(new SavedDataBackedTestStore());
        LandService land = service(
                repository,
                new MutableClock(6_000),
                holders(ALPHA_ID)
        );
        FakePlayerAccess access = fakeAccess(ALPHA_ID);
        DefaultLandClaimService claim = new DefaultLandClaimService(
                land, access, new MutableClock(6_000), 1, 8, "RESIDENTIAL");

        // offline
        access.setOnline(false);
        assertGate(claim, ClaimReceipt.CODE_NOT_PLAYER, "an offline player is fail-closed");
        access.setOnline(true);

        // invalid dimension
        assertGate(claim, ClaimReceipt.CODE_INVALID, "an invalid dimension is fail-closed",
                "not-a-resource-location!");

        // wrong current dimension
        access.setCurrentDimension("minecraft:the_nether");
        assertGate(claim, ClaimReceipt.CODE_WRONG_DIMENSION,
                "a mismatched current dimension is fail-closed");
        access.setCurrentDimension(DIMENSION);

        // not holding the communicator
        access.setHoldsCommunicator(false);
        assertGate(claim, ClaimReceipt.CODE_NOT_HOLDING,
                "not holding the communicator is fail-closed");
        access.setHoldsCommunicator(true);

        // not loaded
        access.setLoaded(false);
        assertGate(claim, ClaimReceipt.CODE_NOT_LOADED, "an unloaded target is fail-closed");
        access.setLoaded(true);

        // out of reach
        access.setInReach(false);
        assertGate(claim, ClaimReceipt.CODE_OUT_OF_REACH, "an out-of-reach target is fail-closed");
        access.setInReach(true);
    }

    private static void assertGate(LandClaimService claim, String expectedCode, String message) {
        assertGate(claim, expectedCode, message, DIMENSION);
    }

    private static void assertGate(
            LandClaimService claim,
            String expectedCode,
            String message,
            String dimension
    ) {
        ClaimReceipt receipt = claim.claim(ALPHA_ID, dimension, 0, 60, 0);
        require(!receipt.success() && receipt.code().equals(expectedCode),
                message + " (code " + receipt.code() + ")");
        InspectResult inspected = claim.inspect(ALPHA_ID, dimension, 0, 60, 0);
        require(!inspected.claimable(), message + " also fails inspect closed");
    }

    // ------------------------------------------------------------------
    // unprovisioned / no active subject fails closed (via the land service)
    // ------------------------------------------------------------------

    private static void testNoSubjectFailsClosed() {
        LandRepository repository = repository(new SavedDataBackedTestStore());
        MutableClock clock = new MutableClock(7_000);
        // ALPHA has a record but no active subject.
        FakeHolderDirectory holders = holders();
        holders.addWithoutSubject(ALPHA_ID);
        LandService land = service(repository, clock, holders);
        FakePlayerAccess access = fakeAccess(ALPHA_ID);
        access.setHoldsCommunicator(true);
        DefaultLandClaimService claim = new DefaultLandClaimService(
                land, access, clock, 1, 8, "RESIDENTIAL");

        ClaimReceipt receipt = claim.claim(ALPHA_ID, DIMENSION, 0, 60, 0);
        require(!receipt.success() && receipt.code().equals(ClaimReceipt.CODE_NO_SUBJECT),
                "a player without an active subject cannot claim (code "
                        + receipt.code() + ")");
        require(repository.size() == 0 && repository.snapshot().storeRevision() == 0L,
                "a no-subject rejection commits nothing");
    }

    // ------------------------------------------------------------------
    // store-failure atomicity + safe retry
    // ------------------------------------------------------------------

    private static void testStoreFailureAtomicAndRetryable() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(8_000);
        LandRepository repository = repository(store);
        LandService land = service(repository, clock, holders(ALPHA_ID));
        DefaultLandClaimService claim = claimService(land, fakeAccess(ALPHA_ID), clock);
        store.setCommitFailureCode("INJECTED_FAILURE");

        ClaimReceipt failure = claim.claim(ALPHA_ID, DIMENSION, 0, 60, 0);
        require(!failure.success() && failure.code().equals(ClaimReceipt.CODE_STORE),
                "a durable-gate rejection maps to CLAIM_STORE");
        require(repository.size() == 0, "a failed claim publishes no parcel");
        require(repository.snapshot().storeRevision() == 0L,
                "a failed claim advances no revision");
        require(store.commitCount() >= 1, "the store was attempted");

        // Recovery: clear the failure and retry the same claim safely.
        store.clearCommitFailure();
        ClaimReceipt retry = claim.claim(ALPHA_ID, DIMENSION, 0, 60, 0);
        require(retry.success(), "a failed claim can be safely retried after recovery");
        require(repository.size() == 1 && repository.snapshot().storeRevision() == 1L,
                "the retry commits exactly one parcel");
    }

    // ------------------------------------------------------------------
    // enforcement: no other player's detail leak, bounded query
    // ------------------------------------------------------------------

    private static void testNoClientParityAndNoOpBypass() throws Exception {
        Path root = Path.of(System.getProperty("fontainerepublic.projectDir", "."))
                .toAbsolutePath().normalize();

        // The no-client command and the C2S handler must resolve the very same
        // ACTIVE LandClaimService, so a command can never bypass the gate.
        Path command = root.resolve("src/main/java/com/fontainerepublic/server/command/LandCommand.java");
        String commandSource = Files.readString(command);
        require(commandSource.contains("runtimeResolver.landClaimService()"),
                "the land command resolves the shared ACTIVE LandClaimService");
        require(!commandSource.contains("land.claim") && !commandSource.contains("createParcel"),
                "the land command never calls the raw land service directly (no bypass)");

        Path handler = root.resolve(
                "src/main/java/com/fontainerepublic/common/landclaim/LandClaimMessageHandlers.java");
        String handlerSource = Files.readString(handler);
        require(handlerSource.contains("LandClaimRuntime.resolve()"),
                "the C2S handler resolves the shared ACTIVE LandClaimService");

        // Server/ never references client/ (side isolation).
        Path server = root.resolve("src/main/java/com/fontainerepublic/server");
        try (var paths = Files.walk(server)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                require(!source.contains("com.fontainerepublic.client."),
                        "server/ must never reference client/: " + path);
            }
        }

        // Land claim module depends only on land + network.
        for (Method method : LandClaimModule.class.getDeclaredMethods()) {
            String name = method.getName();
            require(List.of(
                    "register", "getName", "init", "bindServices",
                    "shutdown", "service"
            ).contains(name),
                    "unexpected land-claim module method: " + name);
        }
    }

    // ------------------------------------------------------------------
    // FR-LAND-CLAIM-001-FIX-01 F4: behavioral command feedback + shared service
    // ------------------------------------------------------------------

    /**
     * Behavioral no-client command parity through executable production logic
     * (not a source scan): the /fr land inspect/claim helpers invoke the very
     * same {@link DefaultLandClaimService} the C2S handlers use, and the
     * success/failure feedback selection is asserted outcome-for-outcome for
     * every branch. OP cannot bypass any gate because the helpers are thin
     * facades over the shared service whose held-communicator / dimension /
     * loaded / reach gates fire regardless of the caller's command level.
     */
    private static void testCommandFeedbackAndSharedService() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(20_000);
        LandRepository repository = repository(store);
        LandService land = service(repository, clock, holders(ALPHA_ID));
        FakePlayerAccess access = fakeAccess(ALPHA_ID);
        // halfWidth=1 height=8 -> region x[-1,1] y[60,67] z[-1,1] around (0,60,0).
        DefaultLandClaimService shared = new DefaultLandClaimService(
                land, access, clock, 1, 8, "RESIDENTIAL");

        // --- inspect feedback selection ---------------------------------
        LandCommand.CommandOutcome claimable = LandCommand.executeInspect(
                shared, DIMENSION, ALPHA_ID, 9, 60, 9);
        require(claimable.success()
                        && claimable.message().contains("unowned and claimable"),
                "inspect feedback selects the claimable branch");

        // First claim the origin so the point becomes owned.
        LandCommand.executeClaim(shared, DIMENSION, ALPHA_ID, 0, 60, 0);
        LandCommand.CommandOutcome ownedResult = LandCommand.executeInspect(
                shared, DIMENSION, ALPHA_ID, 0, 60, 0);
        require(ownedResult.success()
                        && ownedResult.message().contains("is owned by parcel")
                        && ownedResult.message().contains("zone")
                        && ownedResult.message().contains("access"),
                "inspect feedback selects the owned branch with parcel summary");

        access.setHoldsCommunicator(false);
        LandCommand.CommandOutcome gateInspect = LandCommand.executeInspect(
                shared, DIMENSION, ALPHA_ID, 9, 60, 9);
        require(!gateInspect.success()
                        && gateInspect.message().contains("CLAIM_NOT_HOLDING"),
                "inspect feedback selects the gate-failure branch (code echoed)");
        access.setHoldsCommunicator(true);

        // --- claim feedback selection ------------------------------------
        LandCommand.CommandOutcome claimOk = LandCommand.executeClaim(
                shared, DIMENSION, ALPHA_ID, 5, 60, 5);
        require(claimOk.success()
                        && claimOk.message().contains("Parcel created"),
                "claim feedback selects the success branch");

        LandCommand.CommandOutcome alreadyOwned = LandCommand.executeClaim(
                shared, DIMENSION, ALPHA_ID, 5, 60, 5);
        require(!alreadyOwned.success()
                        && alreadyOwned.message().contains("CLAIM_ALREADY_OWNED"),
                "claim feedback selects the already-owned failure branch");

        // --- OP cannot bypass the held-communicator / dimension / loaded /
        //     reach gates: the helper is a thin facade over the shared
        //     service, so an OP-form caller receives the exact gate code.
        access.setHoldsCommunicator(false);
        LandCommand.CommandOutcome opNotHolding = LandCommand.executeClaim(
                shared, DIMENSION, ALPHA_ID, 40, 60, 40);
        require(!opNotHolding.success()
                        && opNotHolding.message().contains("CLAIM_NOT_HOLDING"),
                "a call without the communicator cannot bypass NOT_HOLDING");
        access.setHoldsCommunicator(true);

        access.setCurrentDimension("minecraft:the_nether");
        LandCommand.CommandOutcome opWrongDim = LandCommand.executeClaim(
                shared, DIMENSION, ALPHA_ID, 40, 60, 40);
        require(!opWrongDim.success()
                        && opWrongDim.message().contains("CLAIM_WRONG_DIMENSION"),
                "a caller in another dimension cannot bypass WRONG_DIMENSION");
        access.setCurrentDimension(DIMENSION);

        access.setLoaded(false);
        LandCommand.CommandOutcome opNotLoaded = LandCommand.executeClaim(
                shared, DIMENSION, ALPHA_ID, 40, 60, 40);
        require(!opNotLoaded.success()
                        && opNotLoaded.message().contains("CLAIM_NOT_LOADED"),
                "an unloaded target cannot be claimed via the command");
        access.setLoaded(true);

        access.setInReach(false);
        LandCommand.CommandOutcome opOutOfReach = LandCommand.executeClaim(
                shared, DIMENSION, ALPHA_ID, 40, 60, 40);
        require(!opOutOfReach.success()
                        && opOutOfReach.message().contains("CLAIM_OUT_OF_REACH"),
                "an out-of-reach target cannot be claimed via the command");
        access.setInReach(true);

        // The command succeeded at the origin through the shared service: it
        // must be reflected in the repository (the command and C2S share the
        // same authoritative mutation, never a parallel raw LandService path).
        require(repository.parcelAt(DIMENSION, 0, 60, 0).isPresent(),
                "the command's claim mutates through the shared land repository");
        require(!repository.parcelAt(DIMENSION, 40, 60, 40).isPresent(),
                "no raw/bypass claim reached the 40,60,40 position");
    }

    // ------------------------------------------------------------------
    // packet codecs round-trip + bounds
    // ------------------------------------------------------------------

    private static void testPacketCodecRoundTrip() {
        LandInspectPacket inspect = roundTrip(
                new LandInspectPacket(DIMENSION, 10, 20, 30),
                LandInspectPacket::encode, LandInspectPacket::decode);
        require(inspect.dimension().equals(DIMENSION)
                        && inspect.x() == 10 && inspect.y() == 20 && inspect.z() == 30,
                "LandInspectPacket round-trips");

        LandClaimPacket claim = roundTrip(
                new LandClaimPacket(DIMENSION, 10, 20, 30),
                LandClaimPacket::encode, LandClaimPacket::decode);
        require(claim.dimension().equals(DIMENSION)
                        && claim.x() == 10 && claim.y() == 20 && claim.z() == 30,
                "LandClaimPacket round-trips");

        // FR-LAND-CLAIM-001-FIX-01 F3: result packets carry the canonical
        // request-target echo (dimension + x + y + z) and survive the codec.
        LandInspectResultPacket claimable = roundTrip(
                new LandInspectResultPacket(DIMENSION, 10, 20, 30,
                        true, "INSPECT_OK", null,
                        0, 0, 0, 0, 0, 0, null, null, 1_000L),
                LandInspectResultPacket::encode, LandInspectResultPacket::decode);
        require(claimable.claimable()
                        && DIMENSION.equals(claimable.dimension())
                        && claimable.x() == 10 && claimable.y() == 20
                        && claimable.z() == 30,
                "claimable inspect result round-trips with its target echo");

        LandInspectResultPacket owned = roundTrip(
                new LandInspectResultPacket(DIMENSION, 10, 20, 30,
                        false, "INSPECT_OK", ALPHA_ID,
                        0, 0, 0, 5, 5, 5, "RESIDENTIAL", "PRIVATE", 2_000L),
                LandInspectResultPacket::encode, LandInspectResultPacket::decode);
        require(!owned.claimable() && owned.parcelId().equals(ALPHA_ID)
                        && owned.zoneType().equals("RESIDENTIAL")
                        && DIMENSION.equals(owned.dimension())
                        && owned.x() == 10 && owned.y() == 20 && owned.z() == 30,
                "owned inspect result round-trips with its target echo");

        LandClaimResultPacket ok = roundTrip(
                new LandClaimResultPacket(DIMENSION, 10, 20, 30,
                        true, "CLAIM_OK", ALPHA_ID,
                        0, 0, 0, 5, 5, 5, "RESIDENTIAL", "PRIVATE", 3_000L),
                LandClaimResultPacket::encode, LandClaimResultPacket::decode);
        require(ok.success() && ok.parcelId().equals(ALPHA_ID)
                        && ok.zoneType().equals("RESIDENTIAL")
                        && DIMENSION.equals(ok.dimension())
                        && ok.x() == 10 && ok.y() == 20 && ok.z() == 30,
                "successful claim result round-trips with its target echo");

        LandClaimResultPacket failed = roundTrip(
                new LandClaimResultPacket(DIMENSION, 10, 20, 30,
                        false, "CLAIM_OVERLAP", null,
                        0, 0, 0, 0, 0, 0, null, null, 4_000L),
                LandClaimResultPacket::encode, LandClaimResultPacket::decode);
        require(!failed.success() && failed.code().equals("CLAIM_OVERLAP")
                        && DIMENSION.equals(failed.dimension())
                        && failed.x() == 10 && failed.y() == 20 && failed.z() == 30,
                "failed claim result round-trips with its target echo");
    }

    private static void testPacketValueBounds() {
        expectThrows(IllegalArgumentException.class,
                () -> new LandInspectPacket("", 0, 0, 0),
                "an empty dimension is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new LandInspectPacket("x".repeat(65), 0, 0, 0),
                "an over-long dimension is rejected");
        expectThrows(NullPointerException.class,
                () -> new LandClaimPacket(null, 0, 0, 0),
                "a null claim dimension is rejected");

        expectThrows(NullPointerException.class,
                () -> new LandInspectResultPacket(null, 0, 0, 0, false,
                        "INSPECT_OK", null, 0, 0, 0, 0, 0, 0, null, null, 1_000L),
                "a null echoed inspect dimension is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new LandInspectResultPacket("", 0, 0, 0, false,
                        "INSPECT_OK", null, 0, 0, 0, 0, 0, 0, null, null, 1_000L),
                "an empty echoed inspect dimension is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new LandInspectResultPacket("x".repeat(65), 0, 0, 0,
                        false, "INSPECT_OK", null, 0, 0, 0, 0, 0, 0,
                        null, null, 1_000L),
                "an over-long echoed inspect dimension is rejected");
        expectThrows(NullPointerException.class,
                () -> new LandClaimResultPacket(null, 1, 2, 3, false,
                        "CLAIM_OVERLAP", null, 0, 0, 0, 0, 0, 0, null, null, 1_000L),
                "a null echoed claim dimension is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new LandClaimResultPacket("x".repeat(65), 1, 2, 3,
                        false, "CLAIM_OVERLAP", null, 0, 0, 0, 0, 0, 0,
                        null, null, 1_000L),
                "an over-long echoed claim dimension is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new LandInspectResultPacket(DIMENSION, 0, 0, 0, false,
                        "x".repeat(65), null,
                        0, 0, 0, 0, 0, 0, null, null, 1_000L),
                "an over-long inspect code is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new LandInspectResultPacket(DIMENSION, 0, 0, 0,
                        true, "INSPECT_OK", ALPHA_ID, 0, 0, 0, 1, 1, 1,
                        "Z", "A", 1_000L),
                "a claimable result cannot carry a parcel summary");
        expectThrows(IllegalArgumentException.class,
                () -> new LandInspectResultPacket(DIMENSION, 0, 0, 0, false,
                        "INSPECT_OK", null, 0, 0, 0, 0, 0, 0,
                        null, null, 0L),
                "a non-positive inspect timestamp is rejected");

        expectThrows(NullPointerException.class,
                () -> new LandClaimResultPacket(DIMENSION, 1, 2, 3,
                        true, "CLAIM_OK", null,
                        0, 0, 0, 0, 0, 0, null, null, 1_000L),
                "a successful claim must carry a parcel id");
        expectThrows(IllegalArgumentException.class,
                () -> new LandClaimResultPacket(DIMENSION, 1, 2, 3,
                        false, "CLAIM_OK", ALPHA_ID,
                        0, 0, 0, 0, 0, 0, null, null, 1_000L),
                "a failed claim cannot carry a parcel id");
    }

    // ------------------------------------------------------------------
    // rate policies registered in the production ledger
    // ------------------------------------------------------------------

    private static void testRatePoliciesRegistered() {
        LedgerCollector collector = new LedgerCollector();
        NetworkProductionMessageTable.registerAll(collector);

        assertC2s(collector.byId, 23, LandInspectPacket.class);
        assertC2s(collector.byId, 25, LandClaimPacket.class);

        NetworkMessageSpec<?> inspectResult = collector.byId.get(24);
        require(inspectResult != null
                        && inspectResult.messageClass() == LandInspectResultPacket.class
                        && inspectResult.direction() == NetworkDirection.PLAY_TO_CLIENT,
                "ID 24 is the S2C land inspect result");
        require(inspectResult.rateLimitPolicy().isEmpty(),
                "S2C ID 24 carries no C2S rate policy");

        NetworkMessageSpec<?> claimResult = collector.byId.get(26);
        require(claimResult != null
                        && claimResult.messageClass() == LandClaimResultPacket.class
                        && claimResult.direction() == NetworkDirection.PLAY_TO_CLIENT,
                "ID 26 is the S2C land claim result");
        require(claimResult.rateLimitPolicy().isEmpty(),
                "S2C ID 26 carries no C2S rate policy");

        require(NetworkProductionMessageTable.EXPECTED_MESSAGE_COUNT == 27,
                "the protocol expects twenty-seven ledger messages");
        require(NetworkProtocol.VERSION.equals("8"), "the protocol version is v8");
    }

    private static void assertC2s(
            Map<Integer, NetworkMessageSpec<?>> byId,
            int id,
            Class<?> expected
    ) {
        NetworkMessageSpec<?> spec = byId.get(id);
        require(spec != null && spec.messageClass() == expected,
                "ID " + id + " registers " + expected.getSimpleName());
        require(spec.direction() == NetworkDirection.PLAY_TO_SERVER,
                "ID " + id + " is PLAY_TO_SERVER");
        require(spec.rateLimitPolicy().isPresent(),
                "ID " + id + " carries an explicit C2S rate policy");
        RateLimitPolicy policy = spec.rateLimitPolicy().orElseThrow();
        require(policy.capacity() >= 1 && policy.refillTokens() >= 1,
                "ID " + id + " rate policy is bounded and positive");
    }

    private static void testModuleContract() {
        require(LandClaimModule.MODULE_ID.value().equals("landclaim"),
                "land-claim module id is 'landclaim'");
    }

    // ------------------------------------------------------------------
    // FR-LAND-CLAIM-001-FIX-01 F5: unavailable hard dependency fails closed
    // ------------------------------------------------------------------

    /**
     * If the hard land dependency is unexpectedly unavailable at bind, the
     * module must not NPE at startup: it logs, leaves {@code LandClaimRuntime}
     * unbound and exposes no service, so command/C2S resolution returns empty
     * rather than propagating an exception — outcome-asserted through the real
     * module bind path.
     */
    private static void testUnavailableLandServiceFailsClosed() {
        LandClaimModule module = new LandClaimModule(() -> 30_000L);
        LandClaimRuntime.unbind();

        // A null (unavailable) land service must not throw.
        module.bindServices(null);

        // The service stays unbound: exposing null so command resolution
        // (Optional#map) yields an empty result instead of throwing.
        require(module.service() == null,
                "an unavailable land dependency leaves the service unbound");
        require(LandClaimRuntime.resolve().isEmpty(),
                "an unavailable land dependency leaves the runtime unbound "
                        + "(resolve is empty)");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static LandRepository repository(LandStore store) {
        return new LandRepository(
                store, new LandNbtCodec(), LandLimits.DEFAULT,
                new SequentialParcelIdSource()
        );
    }

    private static LandService service(
            LandRepository repository,
            LongSupplier clock,
            FakeHolderDirectory holders
    ) {
        PermissionResolver resolver = new ConfigDrivenPermissionResolver(
                LandPermissionConfig.DEFAULT, repository, holders, clock
        );
        return new DefaultLandService(repository, clock, holders, resolver);
    }

    private static DefaultLandClaimService claimService(
            LandService land,
            FakePlayerAccess access,
            LongSupplier clock
    ) {
        return new DefaultLandClaimService(land, access, clock, 1, 8, "RESIDENTIAL");
    }

    private static FakePlayerAccess fakeAccess(UUID playerId) {
        FakePlayerAccess access = new FakePlayerAccess();
        access.setOnline(true);
        access.setHoldsCommunicator(true);
        access.setCurrentDimension(DIMENSION);
        access.setLoaded(true);
        access.setInReach(true);
        access.setWorldMinY(0);
        access.setWorldMaxY(319);
        return access;
    }

    private static FakeHolderDirectory holders(UUID... players) {
        FakeHolderDirectory holders = new FakeHolderDirectory();
        for (UUID player : players) {
            holders.add(player);
        }
        return holders;
    }

    private static ParcelRegion region() {
        return new ParcelRegion(10, 20, 30, 40, 50, 60);
    }

    private static <T> T roundTrip(
            T message,
            java.util.function.BiConsumer<T, FriendlyByteBuf> encoder,
            java.util.function.Function<FriendlyByteBuf, T> decoder
    ) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            encoder.accept(message, buffer);
            buffer.readerIndex(0);
            return decoder.apply(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> expected, Runnable action, String message
    ) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return expected.cast(thrown);
            }
            throw new AssertionError(message + " (unexpected: " + thrown + ")", thrown);
        }
        throw new AssertionError(message + " (no exception)");
    }

    private static final class SavedDataBackedTestStore implements LandStore {
        private final ModSavedData savedData = new ModSavedData();
        private String commitFailureCode;
        private int commitCount;

        @Override
        public CompoundTag load() {
            return savedData.getModuleData(LandRepository.MODULE_DATA_KEY).copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            commitCount++;
            if (commitFailureCode != null) {
                return new DurableCommitResult(
                        DurableCommitStatus.FAILED,
                        LandRepository.MODULE_DATA_KEY,
                        0L, 0L, commitFailureCode
                );
            }
            savedData.putModuleData(LandRepository.MODULE_DATA_KEY, snapshot.copy());
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED,
                    LandRepository.MODULE_DATA_KEY,
                    1L, 0L, ""
            );
        }

        private void setCommitFailureCode(String code) {
            this.commitFailureCode = code;
        }

        private void clearCommitFailure() {
            this.commitFailureCode = null;
        }

        private int commitCount() {
            return commitCount;
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
    }

    private static final class SequentialParcelIdSource implements ParcelIdSource {
        private long counter;

        @Override
        public UUID nextUuid() {
            counter++;
            return new UUID(0L, counter);
        }
    }

    private static final class FakeHolderDirectory implements HolderDirectory {
        private final Set<UUID> records = new java.util.HashSet<>();
        private final Set<UUID> activeSubjects = new java.util.HashSet<>();

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

        private void addWithoutSubject(UUID playerId) {
            records.add(playerId);
        }
    }

    /** Controllable fake of the server player surface (headless-testable). */
    private static final class FakePlayerAccess implements ServerLandClaimPlayerAccess {
        private boolean online;
        private boolean holdsCommunicator;
        private String currentDimension;
        private boolean loaded;
        private boolean inReach;
        private int worldMinY;
        private int worldMaxY;

        @Override
        public Optional<ServerPlayer> onlinePlayer(UUID playerId) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID playerId) {
            return online;
        }

        @Override
        public boolean holdsCommunicator(UUID playerId) {
            return holdsCommunicator;
        }

        @Override
        public String currentDimension(UUID playerId) {
            return currentDimension;
        }

        @Override
        public boolean isBlockLoaded(UUID playerId, int x, int y, int z) {
            return loaded;
        }

        @Override
        public boolean withinBlockReach(UUID playerId, int x, int y, int z) {
            return inReach;
        }

        @Override
        public int worldMinY(UUID playerId) {
            return worldMinY;
        }

        @Override
        public int worldMaxY(UUID playerId) {
            return worldMaxY;
        }

        @Override
        public void message(UUID playerId, String text) {
        }

        private void setOnline(boolean online) {
            this.online = online;
        }

        private void setHoldsCommunicator(boolean holdsCommunicator) {
            this.holdsCommunicator = holdsCommunicator;
        }

        private void setCurrentDimension(String currentDimension) {
            this.currentDimension = currentDimension;
        }

        private void setLoaded(boolean loaded) {
            this.loaded = loaded;
        }

        private void setInReach(boolean inReach) {
            this.inReach = inReach;
        }

        private void setWorldMinY(int worldMinY) {
            this.worldMinY = worldMinY;
        }

        private void setWorldMaxY(int worldMaxY) {
            this.worldMaxY = worldMaxY;
        }
    }

    private static final class LedgerCollector implements NetworkMessageRegistration {
        private final Map<Integer, NetworkMessageSpec<?>> byId = new LinkedHashMap<>();

        @Override
        public <MSG> void register(NetworkMessageSpec<MSG> spec) {
            byId.put(spec.id(), spec);
        }
    }
}
