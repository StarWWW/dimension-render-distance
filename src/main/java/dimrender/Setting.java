package dimrender;

/** The two distances that are kept per dimension. */
public enum Setting {
    /** How far chunks are drawn (and requested from the server). */
    RENDER("distance."),
    /** How far the world keeps running: mobs, farms, crops. Only used by singleplayer and LAN hosts. */
    SIMULATION("simulation.");

    /** Prefix of this setting's keys in the config file. */
    final String configPrefix;

    Setting(String configPrefix) {
        this.configPrefix = configPrefix;
    }
}
