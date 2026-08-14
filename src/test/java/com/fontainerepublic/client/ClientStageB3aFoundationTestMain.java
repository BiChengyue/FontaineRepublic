package com.fontainerepublic.client;

import com.fontainerepublic.client.net.ClientPresentationCache;
import com.fontainerepublic.client.view.ClientViewProjection;
import com.fontainerepublic.common.network.NetworkProductionMessageTable;
import com.fontainerepublic.common.network.NetworkProtocol;
import com.fontainerepublic.common.network.display.GovernmentInfoPacket;
import com.fontainerepublic.common.network.display.ParliamentInfoPacket;
import com.fontainerepublic.server.government.api.*;
import com.fontainerepublic.server.government.model.*;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.institution.presentation.InstitutionPresentationSync;
import com.fontainerepublic.server.network.NetworkSendService;
import com.fontainerepublic.server.parliament.api.*;
import com.fontainerepublic.server.parliament.model.*;
import com.fontainerepublic.server.registry.model.OwnerReference;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dependency-free FR-CLIENT-001-IMPL-B3a validation (Level 1-2).
 *
 * <p>Covers the Stage B-3a additions: bounded codecs of the government and
 * parliament summary messages, their cache semantics and display projections
 * (including the empty state), the protocol v4 static contract (7-message
 * ledger, IDs 0-6, PLAY_TO_CLIENT, one-way freeze — the executable ledger
 * assertions live in the network foundation test and are kept in sync), the
 * login snapshot wiring (absent player / empty projections / throwing
 * suppliers produce zero sends and zero exceptions), and the side-isolation
 * source scan over the new files.</p>
 */
public final class ClientStageB3aFoundationTestMain {

    private static final UUID PLAYER = UUID.fromString(
            "00000000-0000-0000-0000-0000000000bb");

    private ClientStageB3aFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testGovernmentCodec();
        testParliamentCodec();
        testProtocolV5Contract();
        testCacheSemantics();
        testGovernmentProjection();
        testParliamentProjection();
        testLoginSyncAbsentPlayerNoOp();
        testLoginSyncEmptyServicesNoOp();
        testLoginSyncThrowingSupplierNoOp();
        testSideIsolationSourceScan();
        System.out.println(
                "[FR-CLIENT-001-B3a] Client stage B-3a foundation validation passed");
    }

    // ------------------------------------------------------------------
    // 1. GovernmentInfoPacket codec
    // ------------------------------------------------------------------

    private static void testGovernmentCodec() {
        GovernmentInfoPacket empty = new GovernmentInfoPacket(List.of(), 1_000L);
        check(roundTrip(empty, GovernmentInfoPacket::encode,
                GovernmentInfoPacket::decode).equals(empty),
                "empty government snapshot round-trips");

        GovernmentInfoPacket filled = new GovernmentInfoPacket(
                List.of(
                        new GovernmentInfoPacket.MinistryEntry(
                                "ab".repeat(32), "Ministry of Finance", 3),
                        new GovernmentInfoPacket.MinistryEntry(
                                "cd".repeat(32), "Ministry of Justice", 0)
                ),
                2_000L
        );
        check(roundTrip(filled, GovernmentInfoPacket::encode,
                GovernmentInfoPacket::decode).equals(filled),
                "filled government snapshot round-trips");
        check(filled.count() == 2, "government snapshot counts entries");

        // bounds: empty id/name, over-long id/name, negative count, over-limit
        // list, non-positive snapshot time
        expectThrows(IllegalArgumentException.class,
                () -> new GovernmentInfoPacket.MinistryEntry("", "x", 0),
                "empty ministry id rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new GovernmentInfoPacket.MinistryEntry("x".repeat(65), "x", 0),
                "over-long ministry id rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new GovernmentInfoPacket.MinistryEntry("ab", "", 0),
                "empty ministry name rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new GovernmentInfoPacket.MinistryEntry("ab", "x".repeat(65), 0),
                "over-long ministry name rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new GovernmentInfoPacket.MinistryEntry("ab", "x", -1),
                "negative positionCount rejected");
        List<GovernmentInfoPacket.MinistryEntry> tooMany = new ArrayList<>();
        for (int index = 0; index <= GovernmentInfoPacket.MAX_MINISTRIES; index++) {
            tooMany.add(new GovernmentInfoPacket.MinistryEntry(
                    "e" + index, "m" + index, index));
        }
        expectThrows(IllegalArgumentException.class,
                () -> new GovernmentInfoPacket(tooMany, 1L),
                "over-limit government list rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new GovernmentInfoPacket(List.of(), 0L),
                "zero government snapshot time rejected");
    }

    // ------------------------------------------------------------------
    // 2. ParliamentInfoPacket codec
    // ------------------------------------------------------------------

    private static void testParliamentCodec() {
        ParliamentInfoPacket empty = new ParliamentInfoPacket(List.of(), 1_000L);
        check(roundTrip(empty, ParliamentInfoPacket::encode,
                ParliamentInfoPacket::decode).equals(empty),
                "empty parliament snapshot round-trips");

        ParliamentInfoPacket filled = new ParliamentInfoPacket(
                List.of(
                        new ParliamentInfoPacket.ProposalEntry(
                                "ab".repeat(32), "VOTING", "ORDINARY",
                                "National budget for the fiscal year"),
                        new ParliamentInfoPacket.ProposalEntry(
                                "cd".repeat(32), "GUARDIAN_REVIEW", "ORGANIC",
                                "Organic law on the judiciary")
                ),
                2_000L
        );
        check(roundTrip(filled, ParliamentInfoPacket::encode,
                ParliamentInfoPacket::decode).equals(filled),
                "filled parliament snapshot round-trips");
        check(filled.count() == 2, "parliament snapshot counts entries");

        expectThrows(IllegalArgumentException.class,
                () -> new ParliamentInfoPacket.ProposalEntry("", "VOTING", "ORDINARY", "t"),
                "empty proposal id rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new ParliamentInfoPacket.ProposalEntry("x".repeat(65), "VOTING", "ORDINARY", "t"),
                "over-long proposal id rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new ParliamentInfoPacket.ProposalEntry("ab", "", "ORDINARY", "t"),
                "empty stage rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new ParliamentInfoPacket.ProposalEntry("ab", "x".repeat(33), "ORDINARY", "t"),
                "over-long stage rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new ParliamentInfoPacket.ProposalEntry("ab", "VOTING", "", "t"),
                "empty normLevel rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new ParliamentInfoPacket.ProposalEntry("ab", "VOTING", "x".repeat(33), "t"),
                "over-long normLevel rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new ParliamentInfoPacket.ProposalEntry("ab", "VOTING", "ORDINARY", ""),
                "empty title rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new ParliamentInfoPacket.ProposalEntry("ab", "VOTING", "ORDINARY", "x".repeat(129)),
                "over-long title rejected");
        List<ParliamentInfoPacket.ProposalEntry> tooMany = new ArrayList<>();
        for (int index = 0; index <= ParliamentInfoPacket.MAX_PROPOSALS; index++) {
            tooMany.add(new ParliamentInfoPacket.ProposalEntry(
                    "e" + index, "VOTING", "ORDINARY", "t" + index));
        }
        expectThrows(IllegalArgumentException.class,
                () -> new ParliamentInfoPacket(tooMany, 1L),
                "over-limit parliament list rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new ParliamentInfoPacket(List.of(), 0L),
                "zero parliament snapshot time rejected");
    }

    // ------------------------------------------------------------------
    // 3. protocol v4 static contract (executable ledger assertions live in
    //    NetworkFoundationTestMain and are kept in sync)
    // ------------------------------------------------------------------

    private static void testProtocolV5Contract() {
        check(NetworkProtocol.VERSION.equals("9"), "Protocol version is 9");
        check(NetworkProductionMessageTable.EXPECTED_MESSAGE_COUNT == 29,
                "Protocol v9 expects exactly twenty-nine ledger messages");
        check(NetworkProductionMessageTable.EXPECTED_MESSAGE_COUNT
                        == 23 + 4 + 2,
                "Ledger grows by the four FR-LAND-CLAIM messages (23-26) "
                        + "and the two FR-LAND-002 messages (27-28)");
    }

    // ------------------------------------------------------------------
    // 4. government / parliament cache semantics
    // ------------------------------------------------------------------

    private static void testCacheSemantics() {
        ClientPresentationCache cache = ClientPresentationCache.instance();
        cache.clear();
        check(cache.governmentSnapshot() == null
                        && cache.parliamentSnapshot() == null,
                "cache starts without institution snapshots");

        GovernmentInfoPacket government = new GovernmentInfoPacket(
                List.of(new GovernmentInfoPacket.MinistryEntry("ab", "Finance", 2)),
                1_000L
        );
        ParliamentInfoPacket parliament = new ParliamentInfoPacket(
                List.of(new ParliamentInfoPacket.ProposalEntry(
                        "cd", "VOTING", "ORDINARY", "Budget")),
                1_000L
        );
        cache.setGovernment(government);
        cache.setParliament(parliament);
        check(government.equals(cache.governmentSnapshot())
                        && parliament.equals(cache.parliamentSnapshot()),
                "cache stores the institution snapshots");

        cache.clear();
        check(cache.governmentSnapshot() == null
                        && cache.parliamentSnapshot() == null,
                "logout clears the institution snapshots");
    }

    // ------------------------------------------------------------------
    // 5. government / parliament projections (including empty states)
    // ------------------------------------------------------------------

    private static void testGovernmentProjection() {
        ClientPresentationCache cache = ClientPresentationCache.instance();
        cache.clear();
        check(ClientViewProjection.governmentLines(cache).size() == 1
                        && ClientViewProjection.governmentLines(cache).get(0)
                        .contains("No government info"),
                "empty cache renders the government placeholder");

        cache.setGovernment(new GovernmentInfoPacket(
                List.of(
                        new GovernmentInfoPacket.MinistryEntry(
                                "ab".repeat(32), "Ministry of Finance", 3),
                        new GovernmentInfoPacket.MinistryEntry(
                                "cd".repeat(32), "Ministry of Justice", 0)
                ),
                1_000L
        ));
        List<String> lines = ClientViewProjection.governmentLines(cache);
        check(lines.size() == 2
                        && lines.get(0).contains("Ministry of Finance")
                        && lines.get(0).contains("(3 positions)")
                        && lines.get(1).contains("Ministry of Justice")
                        && lines.get(1).contains("(0 positions)"),
                "government lines render name and position count");
    }

    private static void testParliamentProjection() {
        ClientPresentationCache cache = ClientPresentationCache.instance();
        cache.clear();
        check(ClientViewProjection.parliamentLines(cache).size() == 1
                        && ClientViewProjection.parliamentLines(cache).get(0)
                        .contains("No parliament info"),
                "empty cache renders the parliament placeholder");

        cache.setParliament(new ParliamentInfoPacket(
                List.of(new ParliamentInfoPacket.ProposalEntry(
                        "cd".repeat(32), "VOTING", "ORDINARY", "Budget law")),
                1_000L
        ));
        List<String> lines = ClientViewProjection.parliamentLines(cache);
        check(lines.size() == 1
                        && lines.get(0).contains("VOTING/ORDINARY")
                        && lines.get(0).contains("Budget law"),
                "parliament line renders stage/normLevel/title");
    }

    // ------------------------------------------------------------------
    // 6. login snapshot wiring: absent/empty/throwing -> zero sends, zero
    //    exceptions (best-effort no-client parity)
    // ------------------------------------------------------------------

    private static void testLoginSyncAbsentPlayerNoOp() {
        AtomicInteger sent = new AtomicInteger();
        NetworkSendService sendService = new NetworkSendService(
                connection -> true,
                (message, connection) -> sent.incrementAndGet()
        );
        InstitutionPresentationSync sync = new InstitutionPresentationSync(
                sendService,
                () -> Optional.of(new EmptyGovernmentService()),
                () -> Optional.of(new EmptyParliamentService()),
                Optional::empty,
                Optional::empty,
                () -> 1_000L,
                uuid -> Optional.empty()
        );
        sync.sync(PLAYER);
        check(sent.get() == 0,
                "absent player produces zero sends and no exceptions");
    }

    private static void testLoginSyncEmptyServicesNoOp() {
        AtomicInteger sent = new AtomicInteger();
        NetworkSendService sendService = new NetworkSendService(
                connection -> true,
                (message, connection) -> sent.incrementAndGet()
        );
        InstitutionPresentationSync sync = new InstitutionPresentationSync(
                sendService,
                Optional::empty,
                Optional::empty,
                Optional::empty,
                Optional::empty,
                () -> 1_000L,
                uuid -> Optional.empty()
        );
        sync.sync(PLAYER);
        check(sent.get() == 0,
                "empty service suppliers produce zero sends and no exceptions");
    }

    private static void testLoginSyncThrowingSupplierNoOp() {
        AtomicInteger sent = new AtomicInteger();
        NetworkSendService sendService = new NetworkSendService(
                connection -> true,
                (message, connection) -> sent.incrementAndGet()
        );
        InstitutionPresentationSync sync = new InstitutionPresentationSync(
                sendService,
                () -> {
                    throw new IllegalStateException("injected government failure");
                },
                () -> {
                    throw new IllegalStateException("injected parliament failure");
                },
                () -> {
                    throw new IllegalStateException("injected justice failure");
                },
                () -> {
                    throw new IllegalStateException("injected land failure");
                },
                () -> 1_000L,
                uuid -> Optional.empty()
        );
        sync.sync(PLAYER);
        check(sent.get() == 0,
                "throwing service suppliers produce zero sends and no exceptions");
    }

    // ------------------------------------------------------------------
    // 7. side isolation source scan
    // ------------------------------------------------------------------

    private static void testSideIsolationSourceScan() throws Exception {
        Path root = Path.of(System.getProperty("fontainerepublic.projectDir", "."))
                .toAbsolutePath().normalize();
        Path display = root.resolve(
                "src/main/java/com/fontainerepublic/common/network/display");
        try (var files = Files.walk(display)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                check(!source.contains("import net.minecraft.client"),
                        "common display must not import net.minecraft.client: " + file);
                check(!source.contains("import com.fontainerepublic.client."),
                        "common display must not import client classes: " + file);
            }
        }
        Path server = root.resolve("src/main/java/com/fontainerepublic/server");
        try (var files = Files.walk(server)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                check(!source.contains("com.fontainerepublic.client."),
                        "server/ must never reference client/: " + file);
            }
        }
    }

    // ------------------------------------------------------------------
    // fake services (only the projection reads are used)
    // ------------------------------------------------------------------

    /** Government service stub: empty projection surface only. */
    private static final class EmptyGovernmentService
            implements com.fontainerepublic.server.government.api.GovernmentService {

        @Override
        public List<MinistryProjection> ministries() {
            return List.of();
        }

        @Override
        public List<PositionProjection> positionsByMinistry(MinistryId ministryId) {
            return List.of();
        }

        @Override
        public MinistryReceipt createMinistry(UUID actor, MinistryDraft draft) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public PositionReceipt createPosition(UUID actor, CreatePositionRequest request) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public AppointmentReceipt appoint(
                UUID actor,
                PositionId positionId,
                OwnerReference holder,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public AppointmentReceipt dismiss(
                UUID actor,
                PositionId positionId,
                String reason,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Optional<Ministry> getMinistry(MinistryId ministryId) {
            return Optional.empty();
        }

        @Override
        public Optional<GovernmentPosition> getPosition(PositionId positionId) {
            return Optional.empty();
        }

        @Override
        public Optional<Office> currentOffice(PositionId positionId) {
            return Optional.empty();
        }
    }

    /** Parliament service stub: empty projection surface only. */
    private static final class EmptyParliamentService
            implements com.fontainerepublic.server.parliament.api.ParliamentService {

        @Override
        public List<ProposalProjection> proposals(long afterSeq, int limit) {
            return List.of();
        }

        @Override
        public ProposalReceipt submitProposal(
                ProposalDraft draft,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public VoteReceipt openVote(UUID actor, ProposalId proposalId, OnSiteContext context) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public VoteReceipt castVote(UUID voter, VoteId voteId, VoteChoice choice, OnSiteContext context) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public BillReceipt closeVoteAndAdvance(UUID actor, VoteId voteId, OnSiteContext context) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public GuardianReceipt submitForGuardianReview(
                UUID actor,
                ProposalId proposalId,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public GuardianReceipt guardianApprove(
                UUID actor,
                ProposalId proposalId,
                GuardianChannel channel,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public GuardianReceipt guardianReturn(
                UUID actor,
                ProposalId proposalId,
                GuardianChannel channel,
                String basis,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public GuardianReceipt guardianRecuse(
                UUID actor,
                ProposalId proposalId,
                GuardianChannel channel,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public GuardianReceipt guardianTimeoutAdvance(
                UUID actor,
                ProposalId proposalId,
                GuardianChannel channel,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public CourtReceipt submitForCourtReview(
                UUID actor,
                ProposalId proposalId,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public CourtReceipt courtReviewPassed(
                UUID actor,
                ProposalId proposalId,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public CourtReceipt courtReviewReturned(
                UUID actor,
                ProposalId proposalId,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public CourtReceipt extendCourtReview(
                UUID actor,
                ProposalId proposalId,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public VoteReceipt openOverrideVote(
                UUID actor,
                ProposalId proposalId,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public VoteReceipt openCourtReVote(
                UUID actor,
                ProposalId proposalId,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public ProposalReceipt submitAmendment(
                AmendmentDraft draft,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public VoteReceipt openParliamentVote(
                UUID actor,
                ProposalId proposalId,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public BillReceipt closeParliamentVoteAndAdvance(
                UUID actor,
                VoteId voteId,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public GuardianReceipt publishAmendment(
                UUID actor,
                ProposalId proposalId,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public ReferendumReceipt openReferendum(
                UUID actor,
                ProposalId proposalId,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public ReferendumReceipt castReferendumVote(
                UUID voter,
                ProposalId proposalId,
                VoteChoice choice,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public ReferendumReceipt closeReferendum(
                UUID actor,
                ProposalId proposalId,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public GuardianReceipt guardianConsentApprove(
                UUID actor,
                ProposalId proposalId,
                GuardianChannel channel,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public GuardianReceipt guardianConsentReject(
                UUID actor,
                ProposalId proposalId,
                GuardianChannel channel,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public GuardianReceipt guardianConsentTimeout(
                UUID actor,
                ProposalId proposalId,
                GuardianChannel channel,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Optional<Bill> bill(BillId billId) {
            return Optional.empty();
        }

        @Override
        public Optional<Referendum> referendum(ProposalId proposalId) {
            return Optional.empty();
        }

        @Override
        public Optional<ProposalStage> stage(ProposalId proposalId) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------
    // harness helpers
    // ------------------------------------------------------------------

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

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> type, Runnable action, String message
    ) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (type.isInstance(thrown)) {
                return type.cast(thrown);
            }
            throw new AssertionError(message + " (unexpected: " + thrown + ")", thrown);
        }
        throw new AssertionError(message + " (no exception thrown)");
    }
}
