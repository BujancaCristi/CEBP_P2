package ro.upt.exchange.s1.platform;

import ro.upt.exchange.s1.domain.Role;

/**
 * The caller, as resolved from the JWT. <b>Owner: Cezar</b> (part of the platform contract).
 *
 * <p>Cezar's JWT filter must set a Spring Security {@code Authentication} whose
 * {@code getPrincipal()} is an instance of this record. Controllers in trading/ receive it as a
 * method argument through {@link AuthenticatedUserArgumentResolver} and never touch
 * Spring Security directly.
 */
public record AuthenticatedUser(long userId, long accountId, String username, Role role) {

    public boolean is(Role r) {
        return role == r;
    }

    public boolean canTrade() {
        return role == Role.TRADER || role == Role.MARKET_MAKER;
    }
}
