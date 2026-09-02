package me.blin4ik322.luckychests.modules.expboost;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * Слушатель увеличения опыта (часть модуля expboost).
 *
 * Ловит все основные источники опыта и умножает их на ExpBoostModule.getMultiplier():
 *  - BlockExpEvent       — руда и другие блоки, дающие опыт при разрушении
 *  - EntityDeathEvent    — опыт с убитых мобов
 *  - FurnaceExtractEvent — опыт при доставании готовых предметов из печи
 *  - PlayerFishEvent     — опыт с успешной рыбалки
 *
 * Модуль можно включать/выключать командой /expboost (см. ExpBoostModule, ExpBoostCommand).
 */
public class ExpListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExp(BlockExpEvent event) {
        if (!ExpBoostModule.isEnabled()) {
            return;
        }
        event.setExpToDrop(ExpBoostModule.apply(event.getExpToDrop()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!ExpBoostModule.isEnabled()) {
            return;
        }
        event.setDroppedExp(ExpBoostModule.apply(event.getDroppedExp()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        if (!ExpBoostModule.isEnabled()) {
            return;
        }
        event.setExpToDrop(ExpBoostModule.apply(event.getExpToDrop()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!ExpBoostModule.isEnabled()) {
            return;
        }
        // Опыт есть только при успешной поимке рыбы/предмета.
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        event.setExpToDrop(ExpBoostModule.apply(event.getExpToDrop()));
    }
}
