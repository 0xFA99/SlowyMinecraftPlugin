package com.slowy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public enum ChatVisibility {
    ANYONE("Anyone", NamedTextColor.GREEN),
    FRIENDS("Friends", NamedTextColor.YELLOW),
    OFF("OFF", NamedTextColor.RED);

    private final String label;
    private final NamedTextColor color;

    ChatVisibility(String label, NamedTextColor color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public Component toComponent() {
        return Component.text(label, color);
    }

    public ChatVisibility next() {
        return switch (this) {
            case ANYONE -> FRIENDS;
            case FRIENDS -> OFF;
            case OFF -> ANYONE;
        };
    }

    public static ChatVisibility fromString(String str, ChatVisibility def) {
        if (str == null) return def;
        try {
            return ChatVisibility.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
