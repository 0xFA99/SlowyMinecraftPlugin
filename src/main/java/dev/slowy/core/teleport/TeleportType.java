package dev.slowy.core.teleport;

import org.jspecify.annotations.NullMarked;

@NullMarked
public enum TeleportType {
    TPA,     // Request sender teleports to target
    TPAHERE  // Request target teleports to sender
}
