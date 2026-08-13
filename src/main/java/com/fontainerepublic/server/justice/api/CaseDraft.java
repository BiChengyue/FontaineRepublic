package com.fontainerepublic.server.justice.api;

import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.justice.model.Case;
import com.fontainerepublic.server.justice.model.CaseId;
import com.fontainerepublic.server.justice.model.CaseType;
import com.fontainerepublic.server.justice.model.Evidence;
import com.fontainerepublic.server.justice.model.EvidenceId;
import com.fontainerepublic.server.justice.model.Verdict;
import com.fontainerepublic.server.justice.model.VerdictId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative case-filing draft (FR-JUS-001-A §3.1/§4).
 *
 * <p>The filing party is taken from the validated on-site context at the
 * final mutation boundary (never from the draft); {@code defendantRef} is an
 * optional canonical player UUID. The title and description are bounded and
 * validated at the service boundary and by the {@link Case} record.</p>
 *
 * @param caseType      closed case type
 * @param title         bounded case title
 * @param description   bounded case description
 * @param defendantRef  optional defendant player UUID
 */
public record CaseDraft(
        CaseType caseType,
        String title,
        String description,
        Optional<UUID> defendantRef
) {

    public CaseDraft {
        caseType = Objects.requireNonNull(caseType, "caseType");
        title = Objects.requireNonNull(title, "title");
        description = Objects.requireNonNull(description, "description");
        defendantRef = defendantRef == null ? Optional.empty() : defendantRef;
    }
}
