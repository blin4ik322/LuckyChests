package me.blin4ik322.luckychests.modules.automelter;

import me.blin4ik322.luckychests.modules.core.PluginModule;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль автоплавки железной и золотой руды.
 * Вся логика фичи живёт в этой папке (modules/automelter):
 *
 *  - AutoMelterModule   — точка входа, регистрация + состояние вкл/выкл
 *  - OreSmeltListener    — BlockBreakEvent: замена руды на слиток + эффект частиц
 *  - MeltCommand         — команда /melt и /плавить
 *
 * Подключается из основного класса через ModuleManager.loadAll(this, new AutoMelterModule()).
 */
public final class AutoMelterModule implements PluginModule {

    private static boolean enabled = true;

    @Override
    public void load(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new OreSmeltListener(), plugin);

        MeltCommand meltCommand = new MeltCommand();
        registerCommand(plugin, "melt", meltCommand);
        registerCommand(plugin, "плавить", meltCommand);
    }

    private void registerCommand(JavaPlugin plugin, String name, MeltCommand executor) {
        if (plugin.getCommand(name) != null) {
            plugin.getCommand(name).setExecutor(executor);
        } else {
            plugin.getLogger().warning("[AutoMelter] команда '" + name
                    + "' не объявлена в plugin.yml — добавьте её в секцию commands.");
        }
    }

    @Override
    public String getName() {
        return "AutoMelter";
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * Переключает состояние модуля и возвращает новое значение.
     */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }
}