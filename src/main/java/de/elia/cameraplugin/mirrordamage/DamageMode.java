package de.elia.cameraplugin.mirrordamage;

/**
 * Available damage transfer modes.
 */
public enum DamageMode {
    /** Mirror the incoming damage 1:1 to the player. */
    MIRROR,
    /** Apply a fixed custom damage value on each hit. */
    CUSTOM,
    /** Disable transferring damage. */
    OFF
}