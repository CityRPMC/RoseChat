package dev.rosewood.rosechat.chat;

import java.util.Locale;

public enum DmPrivacy {

    EVERYONE,
    FRIENDS,
    NOBODY;

    public static DmPrivacy fromString(String value) {
        if (value == null || value.isBlank())
            return EVERYONE;

        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "FRIENDS", "FRIEND" -> FRIENDS;
            case "NOBODY", "NONE", "OFF", "FALSE" -> NOBODY;
            default -> EVERYONE;
        };
    }

}
