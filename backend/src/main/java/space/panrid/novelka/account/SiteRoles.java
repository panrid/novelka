package space.panrid.novelka.account;

/** Changes a person's role on the site. Who may give which role is the admin module's rule. */
public interface SiteRoles {

    void setRole(long accountId, SiteRole role);
}
