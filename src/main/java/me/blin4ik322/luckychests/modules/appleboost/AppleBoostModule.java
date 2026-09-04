package me.blin4ik322.luckychests.modules.appleboost;

import me.blin4ik322.luckychests.modules.core.PluginModule;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль увеличенного шанса выпадения яблок с листвы деревьев.
 * Вся логика фичи живёт в этой папке (modules/appleboost):
 *
 *  - AppleBoostModule    — точка входа, регистрация + состояние вкл/выкл + множитель
 *  - AppleLeafListener   — BlockBreakEvent: пересчитывает шанс выпадения яблока
 *  - AppleChanceCommand  — команда /applechance (только для операторов)
 *
 * По умолчанию используется ванильная база: 0.5% шанс выпадения яблока при разрушении листвы.
 * Множитель применяется к этой величине, так что "/applechance 5" увеличивает шанс в 5 раз.
 */
public final class AppleBoostModule implements PluginModule {

    private static boolean enabled = true;

    // Множитель шанса выпадения яблока. 1.0 = ванильный шанс (эффективно ничего не меняется).
    private static double multiplier = 1.0;

    // Ванильный базовый шанс: 0.5% (один из 200 блоков листвы даст яблоко).
    private static final double BASE_CHANCE = 0.005;

    @Override
    public void load(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new AppleLeafListener(), plugin);

        AppleChanceCommand command = new AppleChanceCommand();
        if (plugin.getCommand("applechance") != null) {
            plugin.getCommand("applechance").setExecutor(command);
        } else {
            plugin.getLogger().warning("[AppleBoost] команда 'applechance' не объявлена в plugin.yml"
                    + " — добавьте её в секцию commands.");
        }
    }

    @Override
    public String getName() {
        return "AppleBoost";
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
     * Вычисляет итоговый шанс выпадения яблока с учётом текущего множителя.
     * Результат всегда в диапазоне [0.0, 1.0].
     */
    public static double calculateChance() {
        double chance = BASE_CHANCE * multiplier;
        return Math.max(0.0, Math.min(1.0, chance));
    }
}
