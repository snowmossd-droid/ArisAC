package dev.aris.arisac.check;

public enum CheckType {
    KILLAURA_DROP_SPRINT ("KillAura-A", "killaura"),
    KILLAURA_GRIM_MOVE   ("KillAura-B", "killaura"),
    KILLAURA_ANGLE       ("KillAura-C", "killaura"),
    AUTO_TOTEM           ("AutoTotem-A", "autototem"),
    REACH                ("Reach-A",    "reach"),
    AIM_ASSIST_JERK      ("AimAssist-A","aimassist"),
    AIM_ASSIST_GCD       ("AimAssist-B","aimassist");

    private final String displayName;
    private final String configKey;

    CheckType(String displayName, String configKey) {
        this.displayName = displayName;
        this.configKey   = configKey;
    }

    public String getDisplayName() { return displayName; }
    public String getConfigKey()   { return configKey; }
}
