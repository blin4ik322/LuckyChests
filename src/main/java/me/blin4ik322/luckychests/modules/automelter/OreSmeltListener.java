package me.blin4ik322.luckychests.modules.automelter;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Слушатель автоплавки железной и золотой руды (часть модуля automelter).
 *
 * При разрушении руды:
 *  1) отменяется стандартный дроп сырой руды;
 *  2) на месте блока спавнится готовый слиток (world.dropItemNaturally),
 *     количество слитков зависит от уровня Fortune на кирке игрока
 *     (та же формула, что использует ваниль для угля/алмаза/лазурита и т.п.);
 *  3) проигрывается короткая вспышка частиц Particle.FLAME (эффект "огонька").
 *
 * Модуль можно включать/выключать командой /melt (см. AutoMelterModule, MeltCommand).
 */
public class OreSmeltListener implements Listener {

    // Параметры вспышки частиц: количество, разброс по осям (0 = точка), скорость/размер частиц.
    private static final int PARTICLE_COUNT = 6;
    private static final double PARTICLE_SPREAD = 0.0;
    private static final double PARTICLE_SPEED = 0.05;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!AutoMelterModule.isEnabled()) {
            return;
        }

        Block block = event.getBlock();
        Material ingot = resolveIngot(block.getType());
        if (ingot == null) {
            return;
        }

        // Если игра и так не собиралась давать дроп (например, сломано не тем инструментом
        // или creative-режим без дропа), не подменяем логику.
        if (!event.isDropItems()) {
            return;
        }

        Player player = event.getPlayer();

        // Отменяем ванильный дроп сырой руды.
        event.setDropItems(false);

        World world = block.getWorld();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);

        // Fortune с кирки в руке увеличивает число слитков по ванильной формуле.
        ItemStack tool = player.getInventory().getItemInMainHand();
        int fortuneLevel = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        int amount = rollFortuneAmount(fortuneLevel);

        // Спавним готовый слиток вместо руды.
        world.dropItemNaturally(center, new ItemStack(ingot, amount));

        // Короткая вспышка "огонька" на месте разрушенного блока.
        world.spawnParticle(
                Particle.FLAME,
                center,
                PARTICLE_COUNT,
                PARTICLE_SPREAD, PARTICLE_SPREAD, PARTICLE_SPREAD,
                PARTICLE_SPEED
        );
    }

    /**
     * Считает итоговое количество слитков с учётом уровня Fortune, по той же
     * формуле, что использует ванильный Minecraft для руд с "дополнительным"
     * дропом (уголь, алмаз, лазурит и т.п.): {@code random(0..level+1) - 1},
     * прибавленное к базовому 1 слитку, но не ниже 1 в итоге.
     *
     * Без Fortune (уровень 0 или инструмент без чар) всегда возвращает 1.
     */
    private int rollFortuneAmount(int fortuneLevel) {
        if (fortuneLevel <= 0) {
            return 1;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int bonus = random.nextInt(fortuneLevel + 2) - 1; // ванильная формула Fortune
        int amount = 1 + Math.max(0, bonus);
        return Math.max(1, amount);
    }

    /**
     * Определяет, какой слиток должен выпасть из данного типа руды.
     * Возвращает null, если блок не поддерживается автоплавкой.
     */
    private Material resolveIngot(Material oreType) {
        switch (oreType) {
            case IRON_ORE:
            case DEEPSLATE_IRON_ORE:
                return Material.IRON_INGOT;
            case GOLD_ORE:
            case DEEPSLATE_GOLD_ORE:
            case NETHER_GOLD_ORE:
                return Material.GOLD_INGOT;
            default:
                return null;
        }
    }
}