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
 * Хранит накопленную "надбавку к награде" каждого игрока и считает по ней
 * динамическую награду (bounty) — сколько очков клан получит за убийство
 * этого игрока прямо сейчас.
 *
 * Формула (таблица экономики, колонки G/H/I):
 *   bounty = min(max, round(base * (1 + bonus)))
 *
 * где base — базовая награда за игрока (H1, по умолчанию 15 очков), max —
 * потолок награды, а bonus — сумма надбавок за всё, что игрок успел убить
 * с момента своей последней смерти:
 *
 *   убийство игрока  → +20% (I1)
 *   убийство визера  → +15% (I2)
 *   убийство дракона → +10% (I3)
 *
 * Надбавки складываются от базовой награды, а не друг от друга: 10 убийств
 * игроков дают base * (1 + 10*0.20) = base * 3. При базовых min=15/max=45
 * это ровно прежняя линейная кривая (15 -> 18 -> 21 -> ... -> 45 за 10
 * убийств), только выраженная процентами, как в таблице.
 *
 * Защита от накрутки: если убийца уже убивал конкретную жертву с момента
 * своего последнего сброса стрика, повторное убийство этой же жертвы не даёт
 * ему ничего — ни очков клану, ни роста собственного стрика. Иначе двое
 * игроков из разных кланов могли бы бесконечно убивать друг друга, получая
 * минимальную награду за каждое убийство. Список уже убитых игроков
 * очищается вместе со стриком (см. {@link #resetStreak}), поэтому вернуть
 * жертве "ценность" можно только собственной смертью.
 *
 * Надбавка игрока сбрасывается только когда его убивает игрок не из его
 * клана — обычная смерть (моб, падение, лава, /kill и т.п.) награду за
 * голову больше не трогает.
 *
 * Данные (надбавки и списки убитых жертв) хранятся в playersbattle.yml и
 * переживают перезапуск сервера/перезаход игрока. Сохранение — сразу
 * после каждого изменения, как и в ClanManager/InvestModule.
 */
public class PlayerBattleManager {

    /** Надбавка к собственной награде за убийство игрока (I1). */
    public static final double PLAYER_KILL_BONUS = 0.20;

    /** Надбавка к собственной награде за убийство визера (I2). */
    public static final double WITHER_KILL_BONUS = 0.15;

    /** Надбавка к собственной награде за убийство дракона (I3). */
    public static final double DRAGON_KILL_BONUS = 0.10;

    private final JavaPlugin plugin;
    private final File file;

    private boolean enabled = false;
    private int minPoints = 15;
    private int maxPoints = 45;

    /** UUID игрока -> накопленная надбавка к его награде (0.20 = +20%). */
    private final Map<UUID, Double> bonuses = new HashMap<>();

    /**
     * Для каждого убийцы — множество жертв, убийство которых уже засчиталось
     * в его текущую серию. Очищается при сбросе надбавки убийцы. Нужно, чтобы
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
        bonuses.clear();
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
                    double bonus = playersSection.getDouble(key + ".bonus",
                            // Совместимость со старым форматом: там лежало число
                            // убийств подряд, каждое из которых давало +20%.
                            playersSection.getInt(key + ".streak", 0) * PLAYER_KILL_BONUS);
                    if (bonus > 0) {
                        bonuses.put(uuid, bonus);
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
                + ", игроков с надбавкой=" + bonuses.size());
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("settings.enabled", enabled);
        yaml.set("settings.min", minPoints);
        yaml.set("settings.max", maxPoints);

        ConfigurationSection playersSection = yaml.createSection("players");
        for (Map.Entry<UUID, Double> entry : bonuses.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            playersSection.set(entry.getKey().toString() + ".bonus", entry.getValue());
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
    // Надбавки и награда
    // ---------------------------------------------------------------

    /** Накопленная надбавка игрока к собственной награде (0.20 = +20%). */
    public synchronized double getBonus(UUID uuid) {
        return bonuses.getOrDefault(uuid, 0.0);
    }

    /** Та же надбавка в целых процентах — для показа игрокам (/bounties). */
    public synchronized int getBonusPercent(UUID uuid) {
        return (int) Math.round(getBonus(uuid) * 100);
    }

    /** Текущая награда за убийство этого игрока прямо сейчас (в очках). */
    public synchronized int getBounty(UUID uuid) {
        return calculateBounty(getBonus(uuid));
    }

    private int calculateBounty(double bonus) {
        if (bonus <= 0) {
            return minPoints;
        }
        long bounty = Math.round(minPoints * (1.0 + bonus));
        return (int) Math.min(maxPoints, bounty);
    }

    /**
     * Игрок совершил засчитываемое убийство игрока (не тимкилл).
     *
     * Возвращает {@code true}, только если он убивает этого {@code victim}
     * впервые с момента своей последней смерти: тогда его надбавка растёт на
     * {@link #PLAYER_KILL_BONUS}, и вызывающий код начисляет клану очки.
     * Повторное убийство того же игрока возвращает {@code false} и не даёт
     * НИЧЕГО — ни надбавки, ни очков.
     *
     * Так закрывается фарм очков "по кругу": двое игроков из разных кланов
     * могли бесконечно убивать друг друга и получать минимальную награду за
     * каждое убийство. Теперь, чтобы одна и та же жертва снова начала
     * приносить очки, убийца должен сам умереть — это сбрасывает его надбавку
     * вместе со списком уже убитых (см. {@link #resetStreak}).
     */
    public synchronized boolean registerKill(UUID killer, UUID victim) {
        Set<UUID> alreadyKilled = killedVictims.computeIfAbsent(killer, k -> new HashSet<>());
        if (alreadyKilled.add(victim)) {
            addBonus(killer, PLAYER_KILL_BONUS);
            return true;
        }
        return false;
    }

    /**
     * Игрок убил босса — его собственная награда за голову растёт на
     * {@code bonus} ({@link #WITHER_KILL_BONUS} / {@link #DRAGON_KILL_BONUS}).
     * В отличие от убийств игроков, повторные убийства боссов ограничиваются
     * не здесь, а кулдауном самого босса.
     */
    public synchronized void registerBossKill(UUID killer, double bonus) {
        addBonus(killer, bonus);
    }

    private void addBonus(UUID player, double bonus) {
        bonuses.merge(player, bonus, Double::sum);
        save();
    }

    /**
     * Надбавка игрока сбрасывается (например, его убил игрок не из его
     * клана) — награда за его голову снова падает до минимума, а список
     * уже убитых им жертв обнуляется, так что следующая его серия снова
     * будет засчитывать убийство любого игрока, включая тех, кого он уже
     * убивал раньше.
     */
    public synchronized void resetStreak(UUID victim) {
        boolean changed = bonuses.remove(victim) != null;
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
        for (UUID uuid : bonuses.keySet()) {
            int bounty = getBounty(uuid);
            if (bounty > minPoints) {
                result.add(new AbstractMap.SimpleEntry<>(uuid, bounty));
            }
        }
        result.sort(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed());
        return result;
    }
}