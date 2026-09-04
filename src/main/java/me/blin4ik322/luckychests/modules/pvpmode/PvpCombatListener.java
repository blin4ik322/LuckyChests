package me.blin4ik322.luckychests.modules.pvpmode;

import me.blin4ik322.luckychests.modules.clans.Clan;
import me.blin4ik322.luckychests.modules.clans.ClanManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Слушатель PVP-режима (часть модуля pvpmode).
 *
 * Отслеживает удары игрока по игроку и делегирует логику таймера в PvpTimerManager.
 * Чтобы не запускать режим для союзников, достаём ClanManager из контекста плагина.
 */
public class PvpCombatListener implements Listener {

    private final PvpTimerManager timerManager;

    // ClanManager подключается лениво через Bukkit.getServicesManager() или
    // передаётся напрямую из LuckyChests.onEnable(). Здесь — прямая передача.
    private ClanManager clanManager;

    /**
     * Конструктор без ClanManager — используется когда модуль загружается
     * раньше ClanManager (например, через ModuleManager.loadAll).
     * В этом случае проверка клана пропускается (null-safe ниже).
     */
    public PvpCombatListener(PvpTimerManager timerManager) {
        this.timerManager = timerManager;
    }

    /** Опциональный setter — вызывается из LuckyChests.onEnable после создания ClanManager. */
    public void setClanManager(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Нас интересуют только удары Player → Player.
        Entity damagerEntity = event.getDamager();
        Entity victimEntity = event.getEntity();

        if (!(damagerEntity instanceof Player) || !(victimEntity instanceof Player)) {
            return;
        }

        Player attacker = (Player) damagerEntity;
        Player victim = (Player) victimEntity;

        // Не запускаем режим для союзников по клану.
        if (areClanmates(attacker, victim)) {
            return;
        }

        // Запускаем/сбрасываем таймер обоим.
        timerManager.startOrReset(attacker, victim);
    }

    /**
     * Обработчик выхода игрока из сервера во время PVP.
     * Если игрок был в активном PVP-режиме — убивает его.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        timerManager.handlePlayerQuit(event.getPlayer().getUniqueId());
    }

    /**
     * Проверяет, состоят ли оба игрока в одном клане.
     * Если ClanManager не подключён — считаем, что кланов нет (режим всегда запускается).
     */
    private boolean areClanmates(Player a, Player b) {
        if (clanManager == null) {
            return false;
        }
        Clan clanA = clanManager.getClanByPlayer(a.getUniqueId());
        Clan clanB = clanManager.getClanByPlayer(b.getUniqueId());

        // Оба должны быть в одном непустом клане (сравниваем по ссылке — один объект).
        return clanA != null && clanA == clanB;
    }
}