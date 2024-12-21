package org.vivecraft.common.network;

public enum Limb {
    MAIN_HAND,
    OFF_HAND,
    RIGHT_FOOT,
    LEFT_FOOT;

    /**
     * @return the opposite limb
     */
    public Limb opposite() {
        return switch (this) {
            case MAIN_HAND -> OFF_HAND;
            case OFF_HAND -> MAIN_HAND;
            case RIGHT_FOOT -> LEFT_FOOT;
            case LEFT_FOOT -> RIGHT_FOOT;
        };
    }
}
