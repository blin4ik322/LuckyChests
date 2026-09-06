package me.blin4ik322.luckychests.modules.lootchests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

/**
 * Ловит открытие инвентаря сундука/бочки и, если это одно из активных
 * хранилищ, запускает обратный отсчёт до его исчезновения.
 *
 * Также запрещает разрушение активных хранилищ игроками и взрывами,
 * и блокирует установку сундуков/бочек на зарегистрированные координаты.
 */
public class LootChestsListener implements Listener {

    private static final Material[] CONTAINER_MATERIALS = {
            Material.CHEST, Material.BARREL, Material.TRAPPED_CHEST
    };

    private final LootChestsModule module;

    public LootChestsListener(LootChestsModule module) {
        this.module = module;
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        Location loc = event.getInventory().getLocation();
        if (loc == null) {
            return;
        }
        module.onChestOpened(loc);
    }

    /**
     * Запрещает игрокам ломать активные хранилища руками или инструментом.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (module.isActiveChest(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * Запрещает ставить сундук/бочку/капкан-сундук на координаты из списка хранилищ.
     * Это предотвращает ситуацию когда игрок занимает слот и новое хранилище не может заспавниться.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Material placed = event.getBlock().getType();
        boolean isContainer = false;
        for (Material m : CONTAINER_MATERIALS) {
            if (placed == m) {
                isContainer = true;
                break;
            }
        }
        if (!isContainer) {
            return;
        }
        if (module.isRegisteredLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                    Component.text("Здесь зарезервировано место для хранилища.", NamedTextColor.RED)
            );
        }
    }

    /**
     * Защита от взрывов существ (крипер, заряженный крипер, TNT-тележка и т.д.).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> module.isActiveChest(block.getLocation()));
    }

    /**
     * Защита от взрывов блоков (TNT, кроп-взрывы и т.д.).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> module.isActiveChest(block.getLocation()));
    }
}