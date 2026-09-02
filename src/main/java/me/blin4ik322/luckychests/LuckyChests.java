package me.blin4ik322.luckychests;

import me.blin4ik322.luckychests.modules.automelter.AutoMelterModule;
import me.blin4ik322.luckychests.modules.clans.ClanCommand;
import me.blin4ik322.luckychests.modules.clans.ClanManager;
import me.blin4ik322.luckychests.modules.clans.ClanTopCommand;
import me.blin4ik322.luckychests.modules.core.ModuleManager;
import me.blin4ik322.luckychests.modules.customwither.CustomWitherModule;
import me.blin4ik322.luckychests.modules.expboost.ExpBoostModule;
import me.blin4ik322.luckychests.modules.witherboost.WitherBoostModule;
import me.blin4ik322.luckychests.modules.WorldBorderTimer;
import me.blin4ik322.luckychests.modules.worldbordertimer.EventCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuckyChests extends JavaPlugin {

    private ClanManager clanManager;
    private WorldBorderTimer worldBorderTimer;

    @Override
    public void onEnable() {
        ModuleManager.loadAll(this,
                new AutoMelterModule(),
                new ExpBoostModule(),
                new WitherBoostModule()
        );

        // Модуль кланов: ClanManager хранит кланы и их очки (ClanScores) в clans.yml
        // и переживает перезагрузку/перезапуск сервера (сохранение — сразу после
        // каждого изменения, см. ClanManager). Очки клана удаляются только вместе
        // с кланом, по команде /clan disband.
        clanManager = new ClanManager(this);
        clanManager.load();

        ClanCommand clanCommand = new ClanCommand(clanManager);
        getCommand("clan").setExecutor(clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);
        getCommand("top").setExecutor(new ClanTopCommand(clanManager));

        // Модуль "Адский Босяк": кастомный визер без звука спавна на весь сервер,
        // с градиентным именем и наградой 50 очков клану за убийство.
        new CustomWitherModule(this, clanManager).enable();

        // Модуль WorldBorderTimer: сужение барьера мира по /event start|stop.
        worldBorderTimer = new WorldBorderTimer(this);
        worldBorderTimer.enable();
        EventCommand eventCommand = new EventCommand(worldBorderTimer);
        getCommand("event").setExecutor(eventCommand);
        getCommand("event").setTabCompleter(eventCommand);

        getLogger().info("LuckyChests успешно запущен!");
    }

    @Override
    public void onDisable() {
        if (clanManager != null) {
            // Дополнительное сохранение на всякий случай — ClanManager и так
            // сохраняет данные после каждого изменения, но лишним не будет.
            clanManager.save();
        }
        getLogger().info("LuckyChests выключен.");
    }
}