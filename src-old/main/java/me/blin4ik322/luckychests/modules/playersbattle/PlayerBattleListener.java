package me.blin4ik322.luckychests.modules.playersbattle;

import me.blin4ik322.luckychests.modules.clans.Clan;
import me.blin4ik322.luckychests.modules.clans.ClanManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Слушает смерти игроков от рук других игроков (PvP) и:
 *  - если убийца и жертва в одном клане — тимкилл, ничего не делаем
 *    (очки не даются, стрик жертвы не сбрасывается — как и просили);
 *  - иначе засчитываем убийство: клан убийцы получает bounty жертвы
 *    (её текущую динамическую награду, см. {@link PlayerBattleManager}),
 *    а стрик жертвы сбрасывается — она снова "стоит" минимум. Стрик
 *    убийцы растёт, только если он убивает эту жертву впервые в рамках
 *    своего текущего стрика — повторные убийства того же игрока очки
 *    клану приносят, но собственную награду убийцы больше не накручивают.
 *
 * Смерти не от игрока (мобы, падение, лава, /kill и т.д.,
 * getKiller() == null, а также самоубийство) стрик и награду умершего
 * НЕ трогают — они сбрасываются только когда убивает игрок не из
 * твоего клана.
 */
public class PlayerBattleListener implements Listener {

    private final PlayerBattleManager manager;
    private final ClanManager clanManager;

    public PlayerBattleListener(PlayerBattleManager manager, ClanManager clanManager) {
        this.manager = manager;
        this.clanManager = clanManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!manager.isEnabled()) {
            return;
        }

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || killer.equals(victim)) {
            // Игрок умер не от руки другого игрока (моб, падение, лава,
            // /kill и т.п.) — его стрик и награда за голову не сбрасываются:
            // это происходит только когда его убивает игрок не из его клана.
            return;
        }

        Clan killerClan = clanManager.getClanByPlayer(killer.getUniqueId());
        Clan victimClan = clanManager.getClanByPlayer(victim.getUniqueId());

        boolean teamKill = killerClan != null && killerClan == victimClan;
        if (teamKill) {
            // Убийство союзника по клану — не в счёт: очки не начисляются
            // и не сбрасываются, стрик убийцы тоже не растёт.
            return;
        }

        int bounty = manager.getBounty(victim.getUniqueId());

        manager.registerKill(killer.getUniqueId(), victim.getUniqueId());
        manager.resetStreak(victim.getUniqueId());

        if (killerClan != null) {
            clanManager.addScore(killerClan, bounty);
            killer.sendMessage(ChatColor.GREEN + "Ты убил " + ChatColor.YELLOW + victim.getName()
                    + ChatColor.GREEN + "! Клан получил " + ChatColor.YELLOW + bounty
                    + ChatColor.GREEN + " очков.");
        } else {
            killer.sendMessage(ChatColor.GRAY + "Ты убил " + ChatColor.YELLOW + victim.getName()
                    + ChatColor.GRAY + " (награда " + bounty
                    + " очков), но ты не состоишь в клане — очки некому начислить.");
        }
    }
}