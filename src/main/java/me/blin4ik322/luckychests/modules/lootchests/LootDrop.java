package me.blin4ik322.luckychests.modules.lootchests;

import org.bukkit.Material;

/**
 * Один возможный предмет дропа хранилища: материал, диапазон количества
 * (мин-макс за одно появление) и шанс появления в процентах (0-100).
 */
public class LootDrop {

    private final Material material;
    private final int min;
    private final int max;
    private final double chance;

    public LootDrop(Material material, int min, int max, double chance) {
        this.material = material;
        this.min = min;
        this.max = max;
        this.chance = chance;
    }

    public Material getMaterial() {
        return material;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public double getChance() {
        return chance;
    }

    /** Используется как уникальный ключ дропа — по имени материала. */
    public String getName() {
        return material.name();
    }
}
