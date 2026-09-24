package com.outland.game.world;

/** Stable persisted block identifiers; append new values rather than reordering. */
public enum BlockType {
    GRASS(0), DIRT(1), STONE(2), WOOD(3), LEAVES(4);
    private final int id;
    BlockType(int id) { this.id = id; }
    public int id() { return id; }
    public static BlockType fromId(int id) {
        for (BlockType type : values()) if (type.id == id) return type;
        throw new IllegalArgumentException("Unknown block id: " + id);
    }
}
