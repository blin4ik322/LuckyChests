package me.blin4ik322.luckychests.modules.customwither;

import me.blin4ik322.luckychests.modules.clans.Clan;
import me.blin4ik322.luckychests.modules.clans.ClanManager;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль "Адский Босяк" — кастомный визер (часть механики custom-mobs).
 *
 * Что делает:
 *  - При любом спавне визера (яйцо призывания, спавнер, команда, постройка из
 *    черепов и т.д.) он превращается в "Адского Босяка": получает
 *    градиентное разноцветное имя над головой и полностью беззвучный спавн
 *    (никто на сервере не слышит характерный рёв/шок-волну обычного визера).
 *  - При убийстве "Адского Босяка" игроком его клан (если игрок состоит
 *    в клане, см. модуль clans) получает {@link #KILL_REWARD} очков
 *    в счётчик ClanScores.
 *
 * Беззвучный спавн реализован через {Wither#setInvulnerabilityTicks(int)}:
 * стандартный "рёв" и ударная волна визера при появлении — это часть анимации
 * неуязвимости (клиент проигрывает звук и партиклы, пока invulnerabilityTicks > 0).
 * Обнуляя это значение сразу в момент спавна, мы отключаем всю анимацию появления
 * вместе со звуком, и визер сразу становится уязвимым.
 */
public class CustomWitherModule implements Listener {

    /** Сколько очков ClanScores получает клан игрока за убийство Адского Босяка. */
    public static final long KILL_REWARD = 400L;

    /** Как выглядит визер над головой — "/// Адский Босяк \\\" с розово-фиолетовым градиентом. */
    private static final String CUSTOM_NAME = buildGradientName();

    private final JavaPlugin plugin;
    private final ClanManager clanManager;
    private final NamespacedKey markerKey;

    public CustomWitherModule(JavaPlugin plugin, ClanManager clanManager) {
        this.plugin = plugin;
        this.clanManager = clanManager;
        this.markerKey = new NamespacedKey(plugin, "hellish_bosyak");
    }

    /** Регистрирует слушатель событий. Вызывается один раз из LuckyChests#onEnable. */
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[CustomWither] модуль \"Адский Босяк\" включён.");
    }

    // ---------------------------------------------------------------
    // Спавн
    // ---------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Wither)) {
            return;
        }

        Wither wither = (Wither) entity;
        transformIntoHellishBosyak(wither);
    }

    private void transformIntoHellishBosyak(Wither wither) {

        wither.setCustomName(CUSTOM_NAME);
        wither.setCustomNameVisible(true);

        // Помечаем сущность, чтобы отличать Адского Босяка от обычных визеров
        // (например, если в будущем на сервере появятся другие способы заспавнить
        // визера, за убийство которых очки начислять не нужно).
        wither.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
    }

    // ---------------------------------------------------------------
    // Смерть / награда клану
    // ---------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Wither)) {
            return;
        }

        Wither wither = (Wither) entity;
        Byte marker = wither.getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE);
        if (marker == null || marker != 1) {
            // Обычный визер, не наш кастомный моб — очки не начисляем.
            return;
        }

        Player killer = wither.getKiller();
        if (killer == null) {
            return;
        }

        Clan clan = clanManager.getClanByPlayer(killer.getUniqueId());
        if (clan == null) {
            killer.sendMessage(ChatColor.YELLOW + "Вы убили Адского Босяка, но вы не состоите в клане — очки не начислены.");
            return;
        }

        clanManager.addScore(clan, KILL_REWARD);

        killer.sendMessage(ChatColor.LIGHT_PURPLE + "Вы убили Адского Босяка! Клану "
                + ChatColor.BOLD + ChatColor.GOLD + clan.getName() + ChatColor.LIGHT_PURPLE + " начислено " + ChatColor.AQUA + KILL_REWARD + ChatColor.LIGHT_PURPLE +" очков.");

        for (java.util.UUID memberId : clan.getMembers()) {
            if (memberId.equals(killer.getUniqueId())) {
                continue;
            }
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(ChatColor.LIGHT_PURPLE + killer.getName()
                        + " убил Адского Босяка! Клану начислено " + KILL_REWARD + " очков.");
            }
        }
    }

    // ---------------------------------------------------------------
    // Градиентное имя
    // ---------------------------------------------------------------

    /**
     * Собирает имя "/// Адский Босяк \\\" с посимвольным RGB-градиентом
     * (розовый -> фиолетовый), заданным дизайнером механики.
     */
    private static String buildGradientName() {
        char[] chars = {
                '◆', ' ',
                'А', 'д', 'с', 'к', 'и', 'й', ' ',
                'Б', 'о', 'с', 'я', 'к', ' ',
                '◆'
        };
        String[] hex = {
                "E24FA6", "E24FA6",
                "E158AD", "DF61B4", "DE69BB", "DC70C1", "DA78C7", "D87FCC", "D87FCC",
                "D58DD6", "D493DA", "D29ADE", "D1A0E2", "D0A6E5", "D0A6E5",
                "CFACE7"
        };

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            builder.append(ChatColor.of("#" + hex[i]));
            if (i == 0) {
                builder.append(ChatColor.BOLD);
            }
            builder.append(chars[i]);
        }
        return builder.toString();
    }
}