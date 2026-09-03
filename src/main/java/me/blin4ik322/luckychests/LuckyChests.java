package me.blin4ik322.luckychests;

import me.blin4ik322.luckychests.modules.announce.AnnounceModule;
import me.blin4ik322.luckychests.modules.automelter.AutoMelterModule;
import me.blin4ik322.luckychests.modules.clans.ClanCommand;
import me.blin4ik322.luckychests.modules.clans.ClanManager;
import me.blin4ik322.luckychests.modules.clans.ClanTopCommand;
import me.blin4ik322.luckychests.modules.core.ModuleManager;
import me.blin4ik322.luckychests.modules.customwither.CustomWitherModule;
import me.blin4ik322.luckychests.modules.expboost.ExpBoostModule;
import me.blin4ik322.luckychests.modules.guide.GuideModule;
import me.blin4ik322.luckychests.modules.invest.InvestModule;
import me.blin4ik322.luckychests.modules.playersbattle.PlayerBattleModule;
import me.blin4ik322.luckychests.modules.pvpmode.PvpCombatListener;
import me.blin4ik322.luckychests.modules.pvpmode.PvpModeModule;
import me.blin4ik322.luckychests.modules.pvpmode.PvpTimerManager;
import me.blin4ik322.luckychests.modules.witherboost.WitherBoostModule;
import me.blin4ik322.luckychests.modules.WorldBorderTimer;
import me.blin4ik322.luckychests.modules.worldbordertimer.EventCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuckyChests extends JavaPlugin {

    private ClanManager clanManager;
    private WorldBorderTimer worldBorderTimer;
    private PlayerBattleModule playerBattleModule;

    @Override
    public void onEnable() {
        // ── Модули без зависимостей ──────────────────────────────────────────
        ModuleManager.loadAll(this,
                new AutoMelterModule(),
                new ExpBoostModule(),
                new WitherBoostModule(),
                new AnnounceModule()
        );

        // ── Кланы ───────────────────────────────────────────────────────────
        // ClanManager хранит кланы и их очки (ClanScores) в clans.yml
        // и переживает перезагрузку/перезапуск сервера (сохранение — сразу после
        // каждого изменения, см. ClanManager). Очки клана удаляются только вместе
        // с кланом, по команде /clan disband.
        clanManager = new ClanManager(this);
        clanManager.load();

        ClanCommand clanCommand = new ClanCommand(clanManager);
        getCommand("clan").setExecutor(clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);
        getCommand("top").setExecutor(new ClanTopCommand(clanManager));

        // ── PVP-режим ────────────────────────────────────────────────────────
        // Подключается ПОСЛЕ ClanManager, чтобы передать его в слушатель
        // (проверка «в одном клане» отсекает союзников).
        PvpTimerManager pvpTimerManager = new PvpTimerManager(this);
        PvpCombatListener pvpListener = new PvpCombatListener(pvpTimerManager);
        pvpListener.setClanManager(clanManager);          // передаём ClanManager
        getServer().getPluginManager().registerEvents(pvpListener, this);

        // ── Кастомный визер ──────────────────────────────────────────────────
        // Без звука спавна, с градиентным именем и наградой 50 очков клану за убийство.
        new CustomWitherModule(this, clanManager).enable();

        // ── WorldBorderTimer ─────────────────────────────────────────────────
        // Сужение барьера мира по /event start|stop.
        worldBorderTimer = new WorldBorderTimer(this);
        worldBorderTimer.enable();
        EventCommand eventCommand = new EventCommand(worldBorderTimer);
        getCommand("event").setExecutor(eventCommand);
        getCommand("event").setTabCompleter(eventCommand);

        // ── Вложения ─────────────────────────────────────────────────────────
        // Виртуальный сундук /invest (/вложить), очки идут в клан игрока.
        InvestModule investModule = new InvestModule(this, clanManager);
        investModule.enable();

        // ── Путеводитель ─────────────────────────────────────────────────────
        // /guide (/гайд, /help, /помощь) — общий обзор фич плагина,
        // актуальный ценник /invest (читает его напрямую из InvestModule),
        // визуальный показ рецепта тотема (GUI) и настоящая регистрация этого рецепта
        // на верстаке сервера. Подключается после InvestModule.
        new GuideModule(this, investModule).enable();

        // ── Награды за игроков ───────────────────────────────────────────────
        // Динамическая награда клану за PvP-убийство, растёт со стриком убийств жертвы
        // и сбрасывается после её смерти.
        // Включается/настраивается командой /playerbattle, список — /bounties.
        playerBattleModule = new PlayerBattleModule(this, clanManager);
        playerBattleModule.enable();

        getLogger().info("LuckyChests успешно запущен!");
    }

    @Override
    public void onDisable() {
        if (clanManager != null) {
            // Дополнительное сохранение на всякий случай — ClanManager и так
            // сохраняет данные после каждого изменения, но лишним не будет.
            clanManager.save();
        }
        if (playerBattleModule != null) {
            playerBattleModule.save();
        }
        getLogger().info("LuckyChests выключен.");
    }
}