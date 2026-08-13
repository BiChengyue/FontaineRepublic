package com.fontainerepublic.server.institutionaccess.persistence;

import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.Facility;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.FacilityState;
import com.fontainerepublic.server.institutionaccess.model.InstitutionType;
import com.fontainerepublic.server.institutionaccess.model.Terminal;
import com.fontainerepublic.server.institutionaccess.model.TerminalId;
import com.fontainerepublic.server.institutionaccess.model.TerminalPosition;
import com.fontainerepublic.server.institutionaccess.model.TerminalState;
import com.fontainerepublic.server.land.model.ParcelId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Strict, versioned, deterministic NBT codec for the
 * {@code institution-access} namespace (FR-INST-002-A §3).
 *
 * <p>Encoding writes the facility and terminal collections in deterministic
 * lexical key order so the same immutable snapshot always produces an
 * equivalent ordered NBT. Decoding accepts only declared fields with exact
 * NBT types, canonical ids, valid enums/dimensions/positions, a non-empty
 * bounded capability set, the integrity digest matching the anchored
 * position, and consistent key/record ids. Unknown newer versions and any
 * inconsistency are rejected; nothing is ever auto-repaired.</p>
 */
public final class InstitutionAccessNbtCodec {

    private static final String STORE_VERSION = "StoreVersion";
    private static final String STORE_REVISION = "StoreRevision";
    private static final String FACILITIES = "Facilities";
    private static final String TERMINALS = "Terminals";

    private static final String FACILITY_VERSION = "FacilityVersion";
    private static final String FACILITY_ID = "FacilityId";
    private static final String INSTITUTION_TYPE = "InstitutionType";
    private static final String PARCEL_ID = "ParcelId";
    private static final String STATE = "State";
    private static final String FACILITY_REVISION = "FacilityRevision";

    private static final String TERMINAL_VERSION = "TerminalVersion";
    private static final String TERMINAL_ID = "TerminalId";
    private static final String POSITION = "Position";
    private static final String CAPABILITIES = "Capabilities";
    private static final String SECURE = "Secure";
    private static final String INTEGRITY = "Integrity";
    private static final String TERMINAL_REVISION = "TerminalRevision";

    private static final String DIMENSION = "Dimension";
    private static final String X = "X";
    private static final String Y = "Y";
    private static final String Z = "Z";

    /** Hard structural caps, independent of the configurable budget. */
    private static final int HARD_MAX_FACILITIES = 1_000;
    private static final int HARD_MAX_TERMINALS = 10_000;
    private static final int HARD_MAX_CAPABILITIES = 8;

    private static final Set<String> STORE_KEYS = Set.of(
            STORE_VERSION, STORE_REVISION, FACILITIES, TERMINALS
    );
    private static final Set<String> FACILITY_KEYS = Set.of(
            FACILITY_VERSION, FACILITY_ID, INSTITUTION_TYPE, PARCEL_ID,
            STATE, FACILITY_REVISION
    );
    private static final Set<String> TERMINAL_KEYS = Set.of(
            TERMINAL_VERSION, TERMINAL_ID, FACILITY_ID, INSTITUTION_TYPE,
            POSITION, CAPABILITIES, STATE, SECURE, INTEGRITY, TERMINAL_REVISION
    );
    private static final Set<String> POSITION_KEYS = Set.of(DIMENSION, X, Y, Z);

    // ------------------------------------------------------------------
    // decode
    // ------------------------------------------------------------------

    /**
     * Decodes and fully validates a namespace snapshot. Empty input is
     * rejected: a present institution-access namespace must be a valid,
     * initialized store.
     */
    public InstitutionAccessStoreSnapshot decode(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            throw invalid(
                    "institution-access namespace is empty; it must be a valid initialized store"
            );
        }

        requireOnlyKeys(root, STORE_KEYS, "institution-access");
        requireType(root, STORE_VERSION, Tag.TAG_INT, "institution-access");
        requireType(root, STORE_REVISION, Tag.TAG_LONG, "institution-access");
        requireType(root, FACILITIES, Tag.TAG_COMPOUND, "institution-access");
        requireType(root, TERMINALS, Tag.TAG_COMPOUND, "institution-access");

        int storeVersion = root.getInt(STORE_VERSION);
        if (storeVersion != InstitutionAccessStoreSnapshot.CURRENT_STORE_VERSION) {
            throw invalid("Unsupported institution-access store version: " + storeVersion);
        }
        long storeRevision = root.getLong(STORE_REVISION);
        if (storeRevision < 0) {
            throw invalid("StoreRevision must not be negative");
        }

        Map<FacilityId, Facility> facilities = decodeFacilities(
                root.getCompound(FACILITIES)
        );
        Map<TerminalId, Terminal> terminals = decodeTerminals(
                root.getCompound(TERMINALS),
                facilities.keySet()
        );

        return new InstitutionAccessStoreSnapshot(
                storeVersion,
                storeRevision,
                facilities,
                terminals
        );
    }

    private Map<FacilityId, Facility> decodeFacilities(CompoundTag tag) {
        if (tag.getAllKeys().size() > HARD_MAX_FACILITIES) {
            throw invalid("Facility count exceeds " + HARD_MAX_FACILITIES);
        }
        Map<FacilityId, Facility> facilities = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            FacilityId facilityId = parseCanonicalFacilityId(key);
            requireType(tag, key, Tag.TAG_COMPOUND, "Facilities");
            Facility facility = decodeFacility(tag.getCompound(key), facilityId);
            if (facilities.put(facilityId, facility) != null) {
                throw invalid("Duplicate facility id: " + facilityId);
            }
        }
        return facilities;
    }

    private Facility decodeFacility(CompoundTag tag, FacilityId expectedId) {
        requireOnlyKeys(tag, FACILITY_KEYS, "facility " + expectedId);
        requireType(tag, FACILITY_VERSION, Tag.TAG_INT, "facility " + expectedId);
        requireType(tag, FACILITY_ID, Tag.TAG_INT_ARRAY, "facility " + expectedId);
        requireType(tag, INSTITUTION_TYPE, Tag.TAG_STRING, "facility " + expectedId);
        requireType(tag, PARCEL_ID, Tag.TAG_INT_ARRAY, "facility " + expectedId);
        requireType(tag, STATE, Tag.TAG_STRING, "facility " + expectedId);
        requireType(tag, FACILITY_REVISION, Tag.TAG_LONG, "facility " + expectedId);

        int facilityVersion = tag.getInt(FACILITY_VERSION);
        if (facilityVersion != Facility.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported facility version for " + expectedId + ": "
                            + facilityVersion
            );
        }
        FacilityId storedId = FacilityId.of(tag.getUUID(FACILITY_ID));
        if (!expectedId.equals(storedId)) {
            throw invalid(
                    "Facilities key " + expectedId + " does not match record facilityId "
                            + storedId
            );
        }
        try {
            return new Facility(
                    facilityVersion,
                    expectedId,
                    enumValue(InstitutionType.class, tag.getString(INSTITUTION_TYPE),
                            "InstitutionType for " + expectedId),
                    ParcelId.of(tag.getUUID(PARCEL_ID)),
                    enumValue(FacilityState.class, tag.getString(STATE),
                            "State for " + expectedId),
                    tag.getLong(FACILITY_REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid facility " + expectedId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private Map<TerminalId, Terminal> decodeTerminals(
            CompoundTag tag,
            Set<FacilityId> knownFacilities
    ) {
        if (tag.getAllKeys().size() > HARD_MAX_TERMINALS) {
            throw invalid("Terminal count exceeds " + HARD_MAX_TERMINALS);
        }
        Map<TerminalId, Terminal> terminals = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            TerminalId terminalId = parseCanonicalTerminalId(key);
            requireType(tag, key, Tag.TAG_COMPOUND, "Terminals");
            Terminal terminal = decodeTerminal(tag.getCompound(key), terminalId);
            if (!knownFacilities.contains(terminal.facilityId())) {
                throw invalid(
                        "Terminal " + terminalId + " references unknown facility "
                                + terminal.facilityId()
                );
            }
            if (terminals.put(terminalId, terminal) != null) {
                throw invalid("Duplicate terminal id: " + terminalId);
            }
        }
        return terminals;
    }

    private Terminal decodeTerminal(CompoundTag tag, TerminalId expectedId) {
        requireOnlyKeys(tag, TERMINAL_KEYS, "terminal " + expectedId);
        requireType(tag, TERMINAL_VERSION, Tag.TAG_INT, "terminal " + expectedId);
        requireType(tag, TERMINAL_ID, Tag.TAG_INT_ARRAY, "terminal " + expectedId);
        requireType(tag, FACILITY_ID, Tag.TAG_INT_ARRAY, "terminal " + expectedId);
        requireType(tag, INSTITUTION_TYPE, Tag.TAG_STRING, "terminal " + expectedId);
        requireType(tag, POSITION, Tag.TAG_COMPOUND, "terminal " + expectedId);
        requireType(tag, CAPABILITIES, Tag.TAG_LIST, "terminal " + expectedId);
        requireType(tag, STATE, Tag.TAG_STRING, "terminal " + expectedId);
        requireType(tag, SECURE, Tag.TAG_BYTE, "terminal " + expectedId);
        requireType(tag, INTEGRITY, Tag.TAG_STRING, "terminal " + expectedId);
        requireType(tag, TERMINAL_REVISION, Tag.TAG_LONG, "terminal " + expectedId);

        int terminalVersion = tag.getInt(TERMINAL_VERSION);
        if (terminalVersion != Terminal.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported terminal version for " + expectedId + ": "
                            + terminalVersion
            );
        }
        TerminalId storedId = TerminalId.of(tag.getUUID(TERMINAL_ID));
        if (!expectedId.equals(storedId)) {
            throw invalid(
                    "Terminals key " + expectedId + " does not match record terminalId "
                            + storedId
            );
        }
        try {
            return new Terminal(
                    terminalVersion,
                    expectedId,
                    FacilityId.of(tag.getUUID(FACILITY_ID)),
                    enumValue(InstitutionType.class, tag.getString(INSTITUTION_TYPE),
                            "InstitutionType for " + expectedId),
                    decodePosition(tag.getCompound(POSITION), expectedId),
                    decodeCapabilities(tag.getList(CAPABILITIES, Tag.TAG_STRING),
                            expectedId),
                    enumValue(TerminalState.class, tag.getString(STATE),
                            "State for " + expectedId),
                    tag.getBoolean(SECURE),
                    tag.getString(INTEGRITY),
                    tag.getLong(TERMINAL_REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid terminal " + expectedId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private TerminalPosition decodePosition(CompoundTag tag, TerminalId terminalId) {
        requireOnlyKeys(tag, POSITION_KEYS, "position of " + terminalId);
        requireType(tag, DIMENSION, Tag.TAG_STRING, "position of " + terminalId);
        requireType(tag, X, Tag.TAG_INT, "position of " + terminalId);
        requireType(tag, Y, Tag.TAG_INT, "position of " + terminalId);
        requireType(tag, Z, Tag.TAG_INT, "position of " + terminalId);
        try {
            return new TerminalPosition(
                    tag.getString(DIMENSION),
                    tag.getInt(X),
                    tag.getInt(Y),
                    tag.getInt(Z)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid position of " + terminalId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private Set<CapabilityClass> decodeCapabilities(ListTag tag, TerminalId terminalId) {
        if (tag.size() > HARD_MAX_CAPABILITIES) {
            throw invalid(
                    "Capability count of " + terminalId + " exceeds "
                            + HARD_MAX_CAPABILITIES
            );
        }
        Set<CapabilityClass> capabilities = new TreeSet<>();
        for (int i = 0; i < tag.size(); i++) {
            capabilities.add(enumValue(
                    CapabilityClass.class,
                    tag.getString(i),
                    "Capabilities of " + terminalId
            ));
        }
        return capabilities;
    }

    // ------------------------------------------------------------------
    // encode
    // ------------------------------------------------------------------

    /**
     * Encodes a validated snapshot deterministically: facilities and
     * terminals are written in sorted lexical key order, capabilities in
     * sorted enum order.
     */
    public CompoundTag encode(InstitutionAccessStoreSnapshot snapshot) {
        CompoundTag root = new CompoundTag();
        root.putInt(STORE_VERSION, snapshot.storeVersion());
        root.putLong(STORE_REVISION, snapshot.storeRevision());

        CompoundTag facilitiesTag = new CompoundTag();
        TreeMap<String, Facility> orderedFacilities = new TreeMap<>();
        snapshot.facilities().forEach(
                (facilityId, facility) -> orderedFacilities.put(
                        facilityId.canonicalKey(), facility
                )
        );
        orderedFacilities.forEach(
                (key, facility) -> facilitiesTag.put(key, encodeFacility(facility))
        );
        root.put(FACILITIES, facilitiesTag);

        CompoundTag terminalsTag = new CompoundTag();
        TreeMap<String, Terminal> orderedTerminals = new TreeMap<>();
        snapshot.terminals().forEach(
                (terminalId, terminal) -> orderedTerminals.put(
                        terminalId.canonicalKey(), terminal
                )
        );
        orderedTerminals.forEach(
                (key, terminal) -> terminalsTag.put(key, encodeTerminal(terminal))
        );
        root.put(TERMINALS, terminalsTag);
        return root;
    }

    private CompoundTag encodeFacility(Facility facility) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(FACILITY_VERSION, facility.schemaVersion());
        tag.putUUID(FACILITY_ID, facility.facilityId().value());
        tag.putString(INSTITUTION_TYPE, facility.institutionType().name());
        tag.putUUID(PARCEL_ID, facility.parcelId().value());
        tag.putString(STATE, facility.state().name());
        tag.putLong(FACILITY_REVISION, facility.facilityRevision());
        return tag;
    }

    private CompoundTag encodeTerminal(Terminal terminal) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TERMINAL_VERSION, terminal.schemaVersion());
        tag.putUUID(TERMINAL_ID, terminal.terminalId().value());
        tag.putUUID(FACILITY_ID, terminal.facilityId().value());
        tag.putString(INSTITUTION_TYPE, terminal.institutionType().name());
        tag.put(POSITION, encodePosition(terminal.position()));
        tag.put(CAPABILITIES, encodeCapabilities(terminal.orderedCapabilities()));
        tag.putString(STATE, terminal.state().name());
        tag.putBoolean(SECURE, terminal.secure());
        tag.putString(INTEGRITY, terminal.integrity());
        tag.putLong(TERMINAL_REVISION, terminal.terminalRevision());
        return tag;
    }

    private CompoundTag encodePosition(TerminalPosition position) {
        CompoundTag tag = new CompoundTag();
        tag.putString(DIMENSION, position.dimension());
        tag.putInt(X, position.x());
        tag.putInt(Y, position.y());
        tag.putInt(Z, position.z());
        return tag;
    }

    private ListTag encodeCapabilities(Set<CapabilityClass> capabilities) {
        ListTag tag = new ListTag();
        for (CapabilityClass capability : capabilities) {
            tag.add(StringTag.valueOf(capability.name()));
        }
        return tag;
    }

    // ------------------------------------------------------------------
    // size
    // ------------------------------------------------------------------

    /** Serialized (uncompressed) size of an encoded namespace snapshot. */
    public int encodedSize(CompoundTag snapshot) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeUnnamedTag(snapshot, new DataOutputStream(out));
            return out.size();
        } catch (IOException failure) {
            return Integer.MAX_VALUE;
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private FacilityId parseCanonicalFacilityId(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value.toLowerCase(Locale.ROOT))) {
                throw invalid("Facilities key is not canonical: " + value);
            }
            return FacilityId.of(parsed);
        } catch (IllegalArgumentException failure) {
            throw invalid("Invalid facility key: " + value, failure);
        }
    }

    private TerminalId parseCanonicalTerminalId(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value.toLowerCase(Locale.ROOT))) {
                throw invalid("Terminals key is not canonical: " + value);
            }
            return TerminalId.of(parsed);
        } catch (IllegalArgumentException failure) {
            throw invalid("Invalid terminal key: " + value, failure);
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw invalid("Unsupported " + field + " value: " + value);
        }
    }

    private static void requireType(CompoundTag tag, String key, int type, String path) {
        if (!tag.contains(key, type)) {
            throw invalid(
                    path + " is missing required field " + key + " or has the wrong type"
            );
        }
    }

    private static void requireOnlyKeys(CompoundTag tag, Set<String> allowed, String path) {
        for (String key : tag.getAllKeys()) {
            if (!allowed.contains(key)) {
                throw invalid(path + " contains unsupported field " + key);
            }
        }
    }

    private static InstitutionAccessNbtException invalid(String message) {
        return new InstitutionAccessNbtException(message);
    }

    private static InstitutionAccessNbtException invalid(String message, Throwable cause) {
        return new InstitutionAccessNbtException(message, cause);
    }
}
