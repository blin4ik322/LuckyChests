package me.blin4ik322.luckychests.modules.pvpmode;

import me.blin4ik322.luckychests.modules.core.PluginModule;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль PVP-режима.
 * Папка modules/pvpmode:
 *
 *  - PvpModeModule      — точка входа, регистрация
 *  - PvpCombatListener  — EntityDamageByEntityEvent: запуск/обновление таймера
 *  - PvpTimerManager    — хранит таймеры, боссбары и задачи на каждую пару игроков
 *
 * Правила:
 *  • Режим запускается только когда оба игрока НЕ в одном клане (или хотя бы один без клана).
 *  • У каждого бойца — свой красный боссбар с отсчётом 10 с.
 *  • Повторный удар (любым из двоих) сбрасывает таймер обоим на 10 с.
 *  • После истечения — боссбар исчезает, внизу экрана (над хп) появляется ActionBar
 *    «Вы вышли из режима ПВП» на 3 с.
 */
public final class PvpModeModule implements PluginModule {

    @Override
    public void load(JavaPlugin plugin) {
        PvpTimerManager timerManager = new PvpTimerManager(plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new PvpCombatListener(timerManager), plugin);
    }

    @Override
    public String getName() {
        return "PvpMode";
    }
}
