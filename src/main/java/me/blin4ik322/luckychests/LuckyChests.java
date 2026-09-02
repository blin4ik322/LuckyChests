package me.blin4ik322.luckychests;

import me.blin4ik322.luckychests.modules.automelter.AutoMelterModule;
import me.blin4ik322.luckychests.modules.core.ModuleManager;
import me.blin4ik322.luckychests.modules.expboost.ExpBoostModule;
import me.blin4ik322.luckychests.modules.witherboost.WitherBoostModule;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuckyChests extends JavaPlugin {

    @Override
    public void onEnable() {
        ModuleManager.loadAll(this,
                new AutoMelterModule(),
                new ExpBoostModule(),
                new WitherBoostModule()
        );
        getLogger().info("LuckyChests успешно запущен!");
    }

    @Override
    public void onDisable() {
        getLogger().info("LuckyChests выключен.");
    }
}
