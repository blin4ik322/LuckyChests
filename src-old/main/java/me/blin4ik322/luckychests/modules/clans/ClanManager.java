package me.blin4ik322.luckychests.modules.clans;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Хранит все кланы, отвечает за загрузку/сохранение в clans.yml
 * и реализует основные операции (создание, вступление, выход, роспуск и т.д.).
 *
 * Файл clans.yml лежит в папке данных плагина (plugins/LuckyChests/clans.yml)
 * и переживает перезапуск/перезагрузку сервера. Сохранение происходит сразу
 * после каждого изменения (создание/роспуск/переименование/изменение состава/
 * начисление очков), поэтому даже при аварийном завершении сервера данные
 * почти всегда остаются актуальными.
 *
 * Очки клана (ClanScores) хранятся прямо внутри Clan#score и удаляются
 * только вместе с кланом — при вызове disbandClan().
 */
public class ClanManager {

    public static final int MAX_NAME_LENGTH = 15;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L}0-9_]{1,15}$");

    private final JavaPlugin plugin;
    private final File file;

    // Ключ — название клана в нижнем регистре (регистронезависимый поиск).
    private final Map<String, Clan> clansByKey = new HashMap<>();
    // Быстрый поиск клана игрока по UUID.
    private final Map<UUID, String> clanKeyByPlayer = new HashMap<>();
    // Активные приглашения: приглашённый игрок -> ключ клана.
    // Приглашения не сохраняются на диск — это ожидаемо (не игровой прогресс,
    // а временное действие); при перезагрузке сервера их нужно будет отправить заново.
    private final Map<UUID, String> pendingInvites = new HashMap<>();

    public ClanManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "clans.yml");
    }

    public static boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    // ---------------------------------------------------------------
    // Загрузка / сохранение
    // ---------------------------------------------------------------

    public synchronized void load() {
        clansByKey.clear();
        clanKeyByPlayer.clear();

        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection clansSection = yaml.getConfigurationSection("clans");
        if (clansSection == null) {
            return;
        }

        for (String key : clansSection.getKeys(false)) {
            ConfigurationSection section = clansSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            String displayName = section.getString("display-name", key);
            String leaderRaw = section.getString("leader");
            if (leaderRaw == null) {
                plugin.getLogger().warning("[Clan] у клана '" + displayName + "' не указан лидер, пропускаю.");
                continue;
            }

            UUID leader;
            try {
                leader = UUID.fromString(leaderRaw);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Clan] некорректный UUID лидера у клана '" + displayName + "', пропускаю.");
                continue;
            }

            Clan clan = new Clan(displayName, leader);
            clan.setScore(section.getLong("score", 0L));

            for (String memberRaw : section.getStringList("members")) {
                try {
                    clan.getMembers().add(UUID.fromString(memberRaw));
                } catch (IllegalArgumentException ignored) {
                    // Пропускаем битые записи.
                }
            }
            clan.getMembers().add(leader); // лидер всегда состоит в собственном клане

            String clanKey = key.toLowerCase();
            clansByKey.put(clanKey, clan);
            for (UUID member : clan.getMembers()) {
                clanKeyByPlayer.put(member, clanKey);
            }
        }

        plugin.getLogger().info("[Clan] загружено кланов: " + clansByKey.size());
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection clansSection = yaml.createSection("clans");

        for (Map.Entry<String, Clan> entry : clansByKey.entrySet()) {
            Clan clan = entry.getValue();
            ConfigurationSection section = clansSection.createSection(entry.getKey());
            section.set("display-name", clan.getName());
            section.set("leader", clan.getLeader().toString());
            section.set("score", clan.getScore());

            List<String> membersRaw = new ArrayList<>();
            for (UUID member : clan.getMembers()) {
                membersRaw.add(member.toString());
            }
            section.set("members", membersRaw);
        }

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[Clan] не удалось сохранить clans.yml", e);
        }
    }

    // ---------------------------------------------------------------
    // Поиск
    // ---------------------------------------------------------------

    public synchronized Clan getClanByPlayer(UUID uuid) {
        String key = clanKeyByPlayer.get(uuid);
        return key == null ? null : clansByKey.get(key);
    }

    public synchronized Clan getClanByName(String name) {
        return clansByKey.get(name.toLowerCase());
    }

    public synchronized boolean nameTaken(String name) {
        return clansByKey.containsKey(name.toLowerCase());
    }

    public synchronized List<Clan> getAllClansSortedByScore() {
        List<Clan> list = new ArrayList<>(clansByKey.values());
        list.sort(Comparator.comparingLong(Clan::getScore).reversed());
        return list;
    }

    /**
     * Возвращает первые {@code limit} кланов, отсортированных по очкам (ClanScores)
     * по убыванию. Используется командой /top.
     */
    public synchronized List<Clan> getTopClans(int limit) {
        List<Clan> sorted = getAllClansSortedByScore();
        if (sorted.size() > limit) {
            return new ArrayList<>(sorted.subList(0, limit));
        }
        return sorted;
    }

    // ---------------------------------------------------------------
    // Операции с кланами
    // ---------------------------------------------------------------

    /**
     * Создаёт новый клан. Возвращает null, если игрок уже состоит в клане
     * или название уже занято.
     */
    public synchronized Clan createClan(String name, UUID leader) {
        if (clanKeyByPlayer.containsKey(leader) || nameTaken(name)) {
            return null;
        }

        Clan clan = new Clan(name, leader);
        String key = name.toLowerCase();
        clansByKey.put(key, clan);
        clanKeyByPlayer.put(leader, key);
        save();
        return clan;
    }

    /**
     * Полностью распускает клан: все участники теряют привязку к клану,
     * клан и его очки (ClanScores) удаляются безвозвратно.
     */
    public synchronized void disbandClan(Clan clan) {
        String key = clan.getName().toLowerCase();
        clansByKey.remove(key);
        for (UUID member : clan.getMembers()) {
            clanKeyByPlayer.remove(member);
        }
        save();
    }

    public enum LeaveResult {
        LEFT, LEADER_MUST_TRANSFER, NOT_IN_CLAN
    }

    /**
     * Игрок покидает клан. Если он был лидером и в клане остались другие участники,
     * выход отклоняется — сначала нужно передать лидерство (/clan leader) или
     * распустить клан (/clan disband). Если лидер был единственным участником,
     * клан распускается автоматически вместе с его выходом.
     */
    public synchronized LeaveResult leaveClan(UUID player) {
        Clan clan = getClanByPlayer(player);
        if (clan == null) {
            return LeaveResult.NOT_IN_CLAN;
        }

        if (clan.isLeader(player)) {
            if (clan.getMembers().size() > 1) {
                return LeaveResult.LEADER_MUST_TRANSFER;
            }
            disbandClan(clan);
            return LeaveResult.LEFT;
        }

        clan.getMembers().remove(player);
        clanKeyByPlayer.remove(player);
        save();
        return LeaveResult.LEFT;
    }

    public synchronized boolean renameClan(Clan clan, String newName) {
        if (nameTaken(newName)) {
            return false;
        }
        String oldKey = clan.getName().toLowerCase();
        String newKey = newName.toLowerCase();

        clansByKey.remove(oldKey);
        clan.setName(newName);
        clansByKey.put(newKey, clan);

        for (UUID member : clan.getMembers()) {
            clanKeyByPlayer.put(member, newKey);
        }
        save();
        return true;
    }

    /**
     * Передаёт лидерство другому участнику того же клана.
     */
    public synchronized boolean transferLeadership(Clan clan, UUID newLeader) {
        if (!clan.getMembers().contains(newLeader)) {
            return false;
        }
        clan.setLeader(newLeader);
        save();
        return true;
    }

    // ---------------------------------------------------------------
    // Приглашения
    // ---------------------------------------------------------------

    /**
     * Приглашает игрока в клан. Возвращает false, если игрок уже состоит в клане.
     */
    public synchronized boolean invite(Clan clan, UUID target) {
        if (clanKeyByPlayer.containsKey(target)) {
            return false;
        }
        pendingInvites.put(target, clan.getName().toLowerCase());
        return true;
    }

    /**
     * Принимает последнее активное приглашение игрока.
     * Возвращает клан, в который вступил игрок, либо null, если приглашений
     * не было, клан приглашения уже не существует, либо игрок уже состоит
     * в другом клане (в двух кланах одновременно быть нельзя).
     */
    public synchronized Clan acceptInvite(UUID player) {
        String clanKey = pendingInvites.remove(player);
        if (clanKey == null || clanKeyByPlayer.containsKey(player)) {
            return null;
        }
        Clan clan = clansByKey.get(clanKey);
        if (clan == null) {
            return null;
        }

        clan.getMembers().add(player);
        clanKeyByPlayer.put(player, clanKey);
        save();
        return clan;
    }

    public synchronized boolean hasPendingInvite(UUID player) {
        return pendingInvites.containsKey(player);
    }

    // ---------------------------------------------------------------
    // Очки (ClanScores)
    // ---------------------------------------------------------------

    /**
     * Прибавляет очки клану (счётчик ClanScores) и сразу сохраняет изменения на диск,
     * чтобы прогресс не терялся при перезагрузке/аварийном завершении сервера.
     */
    public synchronized void addScore(Clan clan, long amount) {
        clan.addScore(amount);
        save();
    }

    /**
     * Явно устанавливает очки клана (например, для админ-команды сброса счёта).
     */
    public synchronized void setScore(Clan clan, long amount) {
        clan.setScore(amount);
        save();
    }
}