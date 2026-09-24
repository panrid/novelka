package space.panrid.novelka.account.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import space.panrid.novelka.account.CurrentUser;
import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.media.ImageKind;
import space.panrid.novelka.media.Images;
import space.panrid.novelka.platform.web.UserFacingException;

/** The signed-in person's own profile and settings. */
@RestController
@RequestMapping("/api/me")
class SettingsController {

    /** Every field is optional: only what is sent changes. */
    record SettingsRequest(String bio, String dmPolicy, Boolean showReading, Boolean adultConfirmed, Boolean showShah) {
    }

    record NickRequest(String nick) {
    }

    record EmailChangeRequest(String email, String password) {
    }

    record PasswordChangeRequest(String currentPassword, String newPassword) {
    }

    private static final Set<String> DM_POLICIES = Set.of("everyone", "nobody");

    private final CurrentUser access;
    private final AccountService service;
    private final AccountRepository accounts;
    private final MeQuery me;
    private final Images images;
    private final Sessions sessions;
    private final Clock clock;

    SettingsController(CurrentUser access, AccountService service, AccountRepository accounts, MeQuery me,
            Images images, Sessions sessions, Clock clock) {
        this.access = access;
        this.service = service;
        this.accounts = accounts;
        this.me = me;
        this.images = images;
        this.sessions = sessions;
        this.clock = clock;
    }

    @PatchMapping
    Me update(@RequestBody SettingsRequest body) {
        Viewer viewer = access.requireSignedIn();
        String bio = body.bio() == null ? null : AccountRules.bio(body.bio());
        if (body.dmPolicy() != null && !DM_POLICIES.contains(body.dmPolicy())) {
            throw UserFacingException.badRequest("Невідоме налаштування повідомлень.");
        }
        OffsetDateTime adultAt = Boolean.TRUE.equals(body.adultConfirmed())
                ? OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC) : null;
        Boolean showShah = viewer.role() == SiteRole.OWNER ? body.showShah() : null;
        accounts.updateSettings(viewer.accountId(), bio, body.dmPolicy(), body.showReading(), adultAt,
                body.adultConfirmed() != null, showShah);
        return me.find(viewer.accountId()).orElseThrow();
    }

    @PostMapping("/nick")
    Me changeNick(@RequestBody NickRequest body) {
        Viewer viewer = access.requireSignedIn();
        service.changeNick(viewer.accountId(), body.nick());
        return me.find(viewer.accountId()).orElseThrow();
    }

    @PostMapping("/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void changeEmail(@RequestBody EmailChangeRequest body) {
        Viewer viewer = access.requireSignedIn();
        service.requestEmailChange(viewer.accountId(), body.email(), body.password());
    }

    @PostMapping("/password")
    Me changePassword(@RequestBody PasswordChangeRequest body, HttpServletRequest request) {
        Viewer viewer = access.requireSignedIn();
        service.changePassword(viewer.accountId(), body.currentPassword(), body.newPassword());
        // Everyone else who knew the old password is out; this device stays in.
        sessions.signOutOtherDevices(viewer.accountId(), request);
        return me.find(viewer.accountId()).orElseThrow();
    }

    @PostMapping("/avatar")
    @Transactional
    Me uploadAvatar(@RequestParam("file") MultipartFile file) {
        Viewer viewer = access.requireSignedIn();
        long imageId = images.store(viewer.accountId(), ImageKind.AVATAR, bytes(file)).id();
        accounts.setAvatar(viewer.accountId(), imageId);
        return me.find(viewer.accountId()).orElseThrow();
    }

    @DeleteMapping("/avatar")
    Me removeAvatar() {
        Viewer viewer = access.requireSignedIn();
        accounts.setAvatar(viewer.accountId(), null);
        return me.find(viewer.accountId()).orElseThrow();
    }

    private static byte[] bytes(MultipartFile file) {
        if (file.isEmpty()) {
            throw UserFacingException.badRequest("Файл порожній.");
        }
        try {
            return file.getBytes();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
