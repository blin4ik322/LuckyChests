package me.blin4ik322.luckychests.modules.playersbattle;

import me.blin4ik322.luckychests.modules.clans.ClanManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль "Награды за игроков" (PlayerBattle).
 *
 * Динамическая система наград за PvP-убийства: чем больше убийств подряд
 * (без смерти) на счету игрока, тем больше очков получит клан того, кто
 * его в итоге убьёт. После смерти счётчик игрока сбрасывается — он снова
 * "стоит" минимум. Убийства союзников по клану не считаются: очки не
 * начисляются и счётчик жертвы не сбрасывается.
 *
 * Управление:
 *  /playerbattle <минимум> <максимум> — включить и задать границы награды.
 *  /playerbattle off — выключить.
 *  /bounties (/награды) — посмотреть, за кого сейчас дают больше минимума.
 *
 * Настройки и счётчики (стрики) хранятся в playersbattle.yml и переживают
 * перезапуск сервера/перезаход игрока — см. {@link PlayerBattleManager}.
 */
public class PlayerBattleModule {

    private final JavaPlugin plugin;
    private final ClanManager clanManager;
    private PlayerBattleManager manager;

    public PlayerBattleModule(JavaPlugin plugin, ClanManager clanManager) {
        this.plugin = plugin;
        this.clanManager = clanManager;
    }

    public void enable() {
        manager = new PlayerBattleManager(plugin);
        manager.load();

        PlayerBattleCommand playerBattleCommand = new PlayerBattleCommand(manager);
        if (plugin.getCommand("playerbattle") != null) {
            plugin.getCommand("playerbattle").setExecutor(playerBattleCommand);
            plugin.getCommand("playerbattle").setTabCompleter(playerBattleCommand);
        } else {
            plugin.getLogger().warning("[PlayerBattle] команда 'playerbattle' не объявлена в plugin.yml — добавьте её.");
        }

        if (plugin.getCommand("bounties") != null) {
            plugin.getCommand("bounties").setExecutor(new BountiesCommand(manager));
        } else {
            plugin.getLogger().warning("[PlayerBattle] команда 'bounties' не объявлена в plugin.yml — добавьте её.");
        }

        Bukkit.getPluginManager().registerEvents(new PlayerBattleListener(manager, clanManager), plugin);
    }

    /**
     * Менеджер надбавок и наград. Нужен модулям боссов (CustomWitherModule,
     * DragonBossModule), чтобы начислять убийце надбавку к его собственной
     * награде за голову. Доступен только после enable().
     */
    public PlayerBattleManager getManager() {
        return manager;
    }

    /** Дополнительное сохранение при выключении плагина (см. LuckyChests#onDisable). */
    public void save() {
        if (manager != null) {
            manager.save();
        }
    }
}
