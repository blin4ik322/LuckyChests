package me.blin4ik322.luckychests.modules.core;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Регистрирует набор модулей одним вызовом из основного класса плагина.
 *
 * Использование в onEnable() (например, в LuckyChests.java):
 *
 *   @Override
 *   public void onEnable() {
 *       ModuleManager.loadAll(this,
 *           new AutoMelterModule()
 *           // , new ScoreCounterModule()   <- новые модули добавляются сюда одной строкой
 *       );
 *   }
 *
 * Чтобы добавить новую фичу:
 *  1) создать папку modules/<название_фичи>;
 *  2) в ней сделать класс <Название>Module implements PluginModule,
 *     а рядом — свои слушатели/команды (по образцу modules/automelter);
 *  3) добавить "new <Название>Module()" в список аргументов loadAll().
 */
public final class ModuleManager {

    private ModuleManager() {
    }

    public static void loadAll(JavaPlugin plugin, PluginModule... modules) {
        for (PluginModule module : modules) {
            module.load(plugin);
            plugin.getLogger().info("[Modules] загружен модуль: " + module.getName());
        }
    }
}