package me.blin4ik322.luckychests.modules.enemypotion;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Слушает выпивание зелий: если выпито "Зелье Чутья Врагов"
 * (опознаётся по PDC-ключу luckychests:radar_potion), активирует радар
 * для игрока и проигрывает короткий звук подтверждения.
 */
public class EnemyPotionListener implements Listener {

    private final EnemyPotionModule module;

    public EnemyPotionListener(EnemyPotionModule module) {
        this.module = module;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!module.isRadarPotion(item)) {
            return;
        }

        Player player = event.getPlayer();
        module.activateRadar(player);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        Component message = Component.text("Радар активирован на 5 минут!", NamedTextColor.GREEN);
        player.sendMessage(message);
    }
}