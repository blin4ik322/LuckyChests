package me.blin4ik322.luckychests.modules.appleboost;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Слушатель увеличенного шанса выпадения яблок с листвы (часть модуля appleboost).
 *
 * При разрушении блока листвы (любого типа: дуб, берёза, ель и т.д.):
 *  1) проверяет, что модуль включён;
 *  2) забрасывает кубик с вероятностью AppleBoostModule.calculateChance();
 *  3) при успехе выбрасывает одно яблоко в мир рядом с блоком листвы.
 *
 * Модуль можно включать/выключать и настраивать множитель командой /applechance
 * (только для операторов, см. AppleChanceCommand).
 */
public class AppleLeafListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!AppleBoostModule.isEnabled()) {
            return;
        }

        Block block = event.getBlock();
        Material material = block.getType();

        // Проверяем, что это листва (работает для всех типов: OAK_LEAVES, BIRCH_LEAVES и т.д.)
        if (!material.toString().endsWith("_LEAVES")) {
            return;
        }

        double chance = AppleBoostModule.calculateChance();

        if (ThreadLocalRandom.current().nextDouble() < chance) {
            // Выбрасываем яблоко в центр блока листвы естественным образом.
            block.getWorld().dropItemNaturally(
                    block.getLocation().add(0.5, 0.5, 0.5),
                    new ItemStack(Material.APPLE, 1)
            );
        }
    }
}
