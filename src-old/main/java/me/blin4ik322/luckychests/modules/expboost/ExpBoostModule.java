package me.blin4ik322.luckychests.modules.expboost;

import me.blin4ik322.luckychests.modules.core.PluginModule;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль увеличения выпадающего опыта.
 * Вся логика фичи живёт в этой папке (modules/expboost):
 *
 *  - ExpBoostModule  — точка входа, регистрация + состояние вкл/выкл + множитель
 *  - ExpListener      — ловит все источники опыта и умножает его
 *  - ExpBoostCommand  — команда /expboost и /опыт
 *
 * Подключается из основного класса через ModuleManager.loadAll(this, new ExpBoostModule()).
 */
public final class ExpBoostModule implements PluginModule {

    private static boolean enabled = true;

    // Множитель опыта. 1.5 = +50% ко всему выпадающему опыту.
    private static double multiplier = 1.5;

    @Override
    public void load(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new ExpListener(), plugin);

        ExpBoostCommand command = new ExpBoostCommand();
        if (plugin.getCommand("expboost") != null) {
            plugin.getCommand("expboost").setExecutor(command);
        } else {
            plugin.getLogger().warning("[ExpBoost] команда 'expboost' не объявлена в plugin.yml"
                    + " — добавьте её в секцию commands.");
        }
    }

    @Override
    public String getName() {
        return "ExpBoost";
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static double getMultiplier() {
        return multiplier;
    }

    public static void setMultiplier(double value) {
        multiplier = value;
    }

    /**
     * Применяет множитель к количеству опыта, округляя до ближайшего целого.
     */
    public static int apply(int rawExp) {
        return (int) Math.round(rawExp * multiplier);
    }
}