package dev.slowy.core.api;

/**
 * Standard lifecycle interface for all SlowyCore2 subsystems and engines.
 */
public interface Lifecycle {

    /**
     * Called when the subsystem is initialized or enabled.
     */
    default void onEnable() {}

    /**
     * Called when the subsystem is shut down or disabled.
     */
    default void onDisable() {}

    /**
     * Called when configurations or cached states are reloaded.
     */
    default void onReload() {}
}
