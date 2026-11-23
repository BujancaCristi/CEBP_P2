package ro.upt.exchange.s1.ops;

import jakarta.validation.constraints.NotNull;
import ro.upt.exchange.s1.domain.IncidentStatus;

public record UpdateIncidentStatusRequest(
        @NotNull IncidentStatus status
) {
}
