package com.fontainerepublic.server.registry.model;

import java.util.Optional;

/**
 * Closed set of concrete subject types (FR-ID-001-A §3.3).
 *
 * <p>Only {@link #NATURAL_PERSON} ({@code 10}) is currently authorized for
 * ordinary allocation. {@link #HYDRO_ARCHON_OFFICE} ({@code 00}) is the exact
 * fixed office subject and has no allocation pool. All other type ranges
 * ({@code 20}–{@code 99}) remain reserved: they create no module, subject,
 * legal status, or permission until a separately reviewed integration assigns
 * a concrete code.</p>
 */
public enum SubjectType {

    NATURAL_PERSON("10", true),
    HYDRO_ARCHON_OFFICE("00", false);

    private final String typeCode;
    private final boolean allocatable;

    SubjectType(String typeCode, boolean allocatable) {
        this.typeCode = typeCode;
        this.allocatable = allocatable;
    }

    /** Two-digit type code prefix of the public number. */
    public String typeCode() {
        return typeCode;
    }

    /** Whether ordinary random allocation exists for this type. */
    public boolean isAllocatable() {
        return allocatable;
    }

    /** Maps a type code to a concrete type; empty for reserved/undefined codes. */
    public static Optional<SubjectType> fromTypeCode(String code) {
        for (SubjectType type : values()) {
            if (type.typeCode.equals(code)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
