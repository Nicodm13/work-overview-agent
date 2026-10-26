package dk.school.workoverviewagent.action;

import dk.school.workoverviewagent.model.ActionDraft;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class ActionApprovalReference {

    private ActionApprovalReference() {
    }

    static String forDraft(ActionDraft draft) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            add(digest, draft.ownerId());
            add(digest, draft.id());
            add(digest, draft.followUpItemId());
            add(digest, draft.actionType().name());
            add(digest, draft.recipients().size());
            draft.recipients().forEach(value -> add(digest, value));
            add(digest, draft.subject());
            add(digest, draft.body());
            add(digest, draft.meetingTitle());
            add(digest, draft.selectedStartsAt() == null ? null : draft.selectedStartsAt().toString());
            add(digest, draft.selectedEndsAt() == null ? null : draft.selectedEndsAt().toString());
            add(digest, draft.agenda().size());
            draft.agenda().forEach(value -> add(digest, value));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void add(MessageDigest digest, String value) {
        var bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        addLength(digest, value == null ? -1 : bytes.length);
        digest.update(bytes);
    }

    private static void add(MessageDigest digest, int value) {
        add(digest, Integer.toString(value));
    }

    private static void addLength(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }
}
