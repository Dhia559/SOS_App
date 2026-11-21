package com.sos.club.common;

/**
 * Roles de la table `roles` (ids fixes du schema).
 * Les roles d'association n'ont pas de club : ils appartiennent a tous les clubs
 * (users.club_id = NULL, et ils voient / recoivent les posts cibles sur n'importe quel club).
 */
public final class Roles {
    private Roles() {}

    public static final int PRESIDENT_ASSOCIATION = 1;
    public static final int MEMBRE_ASSOCIATION = 2;
    public static final int PRESIDENT_CLUB = 3;
    public static final int MEMBRE_CLUB = 4;

    /** Pour le SQL : "role_id IN (" + ASSOCIATION_IDS_SQL + ")". */
    public static final String ASSOCIATION_IDS_SQL = PRESIDENT_ASSOCIATION + ", " + MEMBRE_ASSOCIATION;

    public static boolean isAssociation(Integer roleId) {
        return roleId != null && (roleId == PRESIDENT_ASSOCIATION || roleId == MEMBRE_ASSOCIATION);
    }
}
