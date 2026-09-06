package me.blin4ik322.luckychests.modules.playersbattle;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Хранит "стрик" (кол-во убийств подряд без смерти) каждого игрока и считает
 * по нему динамическую награду (bounty) — сколько очков клан получит за
 * убийство этого игрока прямо сейчас.
 *
 * Формула:
 *   bounty(streak) = min                                   , если streak <= 0
 *   bounty(streak) = max                                   , если streak >= STREAK_FOR_MAX
 *   bounty(streak) = round(min + (max - min) * streak / STREAK_FOR_MAX)   , иначе
 *
 * То есть награда растёт линейно с каждым убийством и достигает потолка
 * (max, задаётся командой /playerbattle) ровно за STREAK_FOR_MAX подряд
 * убийств. Например при min=15, max=45, STREAK_FOR_MAX=10 каждое убийство
 * добавляет +3 очка: 15 -> 18 -> 21 -> 24 -> ... -> 45.
 *
 * Защита от накрутки: если убийца уже убивал конкретную жертву с момента
 * своего последнего сброса стрика, повторное убийство этой же жертвы даёт
 * клану bounty как обычно, но собственный стрик (и, значит, награда за
 * убийцу) больше не растёт — растёт только за новых жертв. Список уже
 * убитых игроков очищается вместе со стриком (см. {@link #resetStreak}).
 *
 * Стрик игрока сбрасывается только когда его убивает игрок не из его
 * клана — обычная смерть (моб, падение, лава, /kill и т.п.) стрик и
 * награду за голову больше не трогает.
 *
 * Данные (стрики и списки убитых жертв) хранятся в playersbattle.yml и
 * переживают перезапуск сервера/перезаход игрока. Сохранение — сразу
 * после каждого изменения, как и в ClanManager/InvestModule.
 */
public class PlayerBattleManager {

    /** Сколько убийств подряд нужно, чтобы награда за игрока достигла максимума. */
    private static final int STREAK_FOR_MAX = 10;

    private final JavaPlugin plugin;
    private final File file;

    private boolean enabled = false;
    private int minPoints = 15;
    private int maxPoints = 45;

    private final Map<UUID, Integer> streaks = new HashMap<>();

    /**
     * Для каждого убийцы — множество жертв, убийство которых уже засчиталось
     * в его текущий стрик. Очищается при сбросе стрика убийцы. Нужно, чтобы
     * нельзя было накручивать себе награду, убивая одного и того же игрока
     * по кругу — очки клану за это всё равно начисляются, а стрик убийцы
     * растёт только за новых жертв.
     */
    private final Map<UUID, Set<UUID>> killedVictims = new HashMap<>();

    public PlayerBattleManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playersbattle.yml");
    }

    // ---------------------------------------------------------------
    // Загрузка / сохранение
    // ---------------------------------------------------------------

    public synchronized void load() {
        streaks.clear();
        killedVictims.clear();

        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        enabled = yaml.getBoolean("settings.enabled", false);
        minPoints = yaml.getInt("settings.min", 15);
        maxPoints = yaml.getInt("settings.max", 45);

        ConfigurationSection playersSection = yaml.getConfigurationSection("players");
        if (playersSection != null) {
            for (String key : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int streak = playersSection.getInt(key + ".streak", 0);
                    if (streak > 0) {
                        streaks.put(uuid, streak);
                    }

                    List<String> killedList = playersSection.getStringList(key + ".killed");
                    if (killedList != null && !killedList.isEmpty()) {
                        Set<UUID> victims = new HashSet<>();
                        for (String rawVictim : killedList) {
                            try {
                                victims.add(UUID.fromString(rawVictim));
                            } catch (IllegalArgumentException ignored) {
                                // Битая запись — пропускаем.
                            }
                        }
                        if (!victims.isEmpty()) {
                            killedVictims.put(uuid, victims);
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // Битая запись — пропускаем.
                }
            }
        }

        plugin.getLogger().info("[PlayerBattle] загружено: enabled=" + enabled
                + ", min=" + minPoints + ", max=" + maxPoints
                + ", игроков со стриком=" + streaks.size());
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("settings.enabled", enabled);
        yaml.set("settings.min", minPoints);
        yaml.set("settings.max", maxPoints);

        ConfigurationSection playersSection = yaml.createSection("players");
        for (Map.Entry<UUID, Integer> entry : streaks.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            playersSection.set(entry.getKey().toString() + ".streak", entry.getValue());
        }
        for (Map.Entry<UUID, Set<UUID>> entry : killedVictims.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            List<String> victims = new ArrayList<>();
            for (UUID victim : entry.getValue()) {
                victims.add(victim.toString());
            }
            playersSection.set(entry.getKey().toString() + ".killed", victims);
        }

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[PlayerBattle] не удалось сохранить playersbattle.yml", e);
        }
    }

    // ---------------------------------------------------------------
    // Настройки (/playerbattle <min> <max> | off)
    // ---------------------------------------------------------------

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized int getMinPoints() {
        return minPoints;
    }

    public synchronized int getMaxPoints() {
        return maxPoints;
    }

    /**
     * Включает функцию и задаёт границы награды. Возвращает false, если
     * границы некорректны (min <= 0 или max <= min) — в этом случае ничего
     * не меняется.
     */
    public synchronized boolean configure(int min, int max) {
        if (min <= 0 || max <= min) {
            return false;
        }
        this.minPoints = min;
        this.maxPoints = max;
        this.enabled = true;
        save();
        return true;
    }

    public synchronized void disable() {
        this.enabled = false;
        save();
    }

    // ---------------------------------------------------------------
    // Стрики и награда
    // ---------------------------------------------------------------

    public synchronized int getStreak(UUID uuid) {
        return streaks.getOrDefault(uuid, 0);
    }

    /** Текущая награда за убийство этого игрока прямо сейчас (в очках). */
    public synchronized int getBounty(UUID uuid) {
        return calculateBounty(getStreak(uuid));
    }

    private int calculateBounty(int streak) {
        if (streak <= 0) {
            return minPoints;
        }
        if (streak >= STREAK_FOR_MAX) {
            return maxPoints;
        }
        double progress = (double) streak / STREAK_FOR_MAX;
        return (int) Math.round(minPoints + (maxPoints - minPoints) * progress);
    }

    /**
     * Игрок совершил засчитываемое убийство (не тимкилл). Если он убивает
     * этого {@code victim} впервые с момента своего последнего сброса
     * стрика — стрик растёт, награда за будущее убийство увеличивается.
     * Если этого же {@code victim} он уже убивал ранее в рамках текущего
     * стрика — очки клану всё равно начисляются (это делает вызывающий
     * код), но сам стрик убийцы не растёт, чтобы нельзя было накручивать
     * себе награду, убивая одного и того же игрока по кругу.
     */
    public synchronized void registerKill(UUID killer, UUID victim) {
        Set<UUID> alreadyKilled = killedVictims.computeIfAbsent(killer, k -> new HashSet<>());
        if (alreadyKilled.add(victim)) {
            int current = streaks.getOrDefault(killer, 0);
            streaks.put(killer, current + 1);
            save();
        }
    }

    /**
     * Стрик игрока сбрасывается (например, его убил игрок не из его
     * клана) — награда за его голову снова падает до минимума, а список
     * уже убитых им жертв обнуляется, так что следующий его стрик снова
     * будет засчитывать убийство любого игрока, включая тех, кого он уже
     * убивал раньше.
     */
    public synchronized void resetStreak(UUID victim) {
        boolean changed = streaks.remove(victim) != null;
        changed = killedVictims.remove(victim) != null || changed;
        if (changed) {
            save();
        }
    }

    /**
     * Список игроков, у которых награда за убийство выше минимума прямо
     * сейчас, отсортированный по убыванию награды. Используется командой
     * /bounties.
     */
    public synchronized List<Map.Entry<UUID, Integer>> getBounties() {
        List<Map.Entry<UUID, Integer>> result = new ArrayList<>();
        for (UUID uuid : streaks.keySet()) {
            int bounty = getBounty(uuid);
            if (bounty > minPoints) {
                result.add(new AbstractMap.SimpleEntry<>(uuid, bounty));
            }
        }
        result.sort(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed());
        return result;
    }
}