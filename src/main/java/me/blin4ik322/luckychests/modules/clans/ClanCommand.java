package me.blin4ik322.luckychests.modules.clans;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Обработчик команды /clan (часть модуля clan).
 *
 * Подкоманды:
 *  /clan create {название}   — создать клан (1-15 символов, буквы/цифры, без пробелов)
 *  /clan leave                — выйти из клана
 *  /clan disband               — распустить клан (только лидер); клан и его очки удаляются
 *  /clan invite {ник}          — пригласить игрока в клан (только лидер)
 *  /clan accept                 — принять последнее приглашение (в двух кланах быть нельзя)
 *  /clan rename {название}     — переименовать клан (только лидер)
 *  /clan leader {ник}           — передать лидерство участнику клана (только лидер)
 *  /clan score {клан} {кол-во} — начислить очки клану (ClanScores), только для op /
 *                                 luckychests.clan.score
 */
public class ClanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "create", "leave", "disband", "invite", "accept", "rename", "leader", "score"
    );

    private final ClanManager manager;

    public ClanCommand(ClanManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эта команда доступна только игрокам.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                handleCreate(player, args);
                break;
            case "leave":
                handleLeave(player);
                break;
            case "disband":
                handleDisband(player);
                break;
            case "invite":
                handleInvite(player, args);
                break;
            case "accept":
                handleAccept(player);
                break;
            case "rename":
                handleRename(player, args);
                break;
            case "leader":
                handleLeader(player, args);
                break;
            case "score":
                handleScore(player, args);
                break;
            default:
                sendUsage(player);
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Команды кланов:");
        player.sendMessage(ChatColor.YELLOW + "/clan create <название> " + ChatColor.GRAY + "- создать клан");
        player.sendMessage(ChatColor.YELLOW + "/clan leave " + ChatColor.GRAY + "- покинуть клан");
        player.sendMessage(ChatColor.YELLOW + "/clan disband " + ChatColor.GRAY + "- распустить клан");
        player.sendMessage(ChatColor.YELLOW + "/clan invite <ник> " + ChatColor.GRAY + "- пригласить игрока");
        player.sendMessage(ChatColor.YELLOW + "/clan accept " + ChatColor.GRAY + "- принять приглашение");
        player.sendMessage(ChatColor.YELLOW + "/clan rename <название> " + ChatColor.GRAY + "- переименовать клан");
        player.sendMessage(ChatColor.YELLOW + "/clan leader <ник> " + ChatColor.GRAY + "- передать лидерство");
        if (player.hasPermission("luckychests.clan.score")) {
            player.sendMessage(ChatColor.YELLOW + "/clan score <клан> <кол-во> " + ChatColor.GRAY
                    + "- начислить очки клану");
        }
        player.sendMessage(ChatColor.YELLOW + "/top " + ChatColor.GRAY + "- топ кланов по очкам");
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan create <название>");
            return;
        }
        String name = args[1];

        if (!ClanManager.isValidName(name)) {
            player.sendMessage(ChatColor.RED + "Название клана должно быть от 1 до "
                    + ClanManager.MAX_NAME_LENGTH + " символов (буквы, цифры, без пробелов).");
            return;
        }

        if (manager.getClanByPlayer(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + "Вы уже состоите в клане. Сначала выйдите из него (/clan leave).");
            return;
        }

        Clan clan = manager.createClan(name, player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Клан с таким названием уже существует.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Клан \"" + clan.getName() + "\" успешно создан! Вы его лидер.");
    }

    private void handleLeave(Player player) {
        ClanManager.LeaveResult result = manager.leaveClan(player.getUniqueId());
        switch (result) {
            case LEFT:
                player.sendMessage(ChatColor.YELLOW + "Вы покинули клан.");
                break;
            case LEADER_MUST_TRANSFER:
                player.sendMessage(ChatColor.RED + "Вы лидер клана. Сначала передайте лидерство"
                        + " (/clan leader <ник>) или распустите клан (/clan disband).");
                break;
            case NOT_IN_CLAN:
            default:
                player.sendMessage(ChatColor.RED + "Вы не состоите в клане.");
        }
    }

    private void handleDisband(Player player) {
        Clan clan = manager.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане.");
            return;
        }
        if (!clan.isLeader(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Распустить клан может только его лидер.");
            return;
        }

        String name = clan.getName();
        for (UUID memberId : clan.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline() && !memberId.equals(player.getUniqueId())) {
                member.sendMessage(ChatColor.RED + "Клан \"" + name + "\" был распущен лидером.");
            }
        }

        manager.disbandClan(clan);
        player.sendMessage(ChatColor.YELLOW + "Клан \"" + name + "\" распущен, статистика удалена.");
    }

    private void handleInvite(Player player, String[] args) {
        Clan clan = manager.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане.");
            return;
        }
        if (!clan.isLeader(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Приглашать игроков может только лидер клана.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan invite <ник>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.RED + "Игрок не найден или не в сети.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Нельзя пригласить самого себя.");
            return;
        }
        if (manager.getClanByPlayer(target.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + "Этот игрок уже состоит в клане.");
            return;
        }

        if (!manager.invite(clan, target.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Не удалось пригласить игрока.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Приглашение отправлено игроку " + target.getName() + ".");
        target.sendMessage(ChatColor.YELLOW + "Вас пригласили в клан \"" + clan.getName()
                + "\". Введите " + ChatColor.GOLD + "/clan accept" + ChatColor.YELLOW + " чтобы вступить.");
    }

    private void handleAccept(Player player) {
        if (manager.getClanByPlayer(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + "Вы уже состоите в клане.");
            return;
        }
        if (!manager.hasPendingInvite(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "У вас нет активных приглашений в клан.");
            return;
        }

        Clan clan = manager.acceptInvite(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Не удалось принять приглашение (клан мог уже исчезнуть).");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Вы вступили в клан \"" + clan.getName() + "\"!");
        for (UUID memberId : clan.getMembers()) {
            if (memberId.equals(player.getUniqueId())) {
                continue;
            }
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(ChatColor.YELLOW + player.getName() + " вступил в клан!");
            }
        }
    }

    private void handleRename(Player player, String[] args) {
        Clan clan = manager.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане.");
            return;
        }
        if (!clan.isLeader(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Переименовать клан может только лидер.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan rename <название>");
            return;
        }

        String newName = args[1];
        if (!ClanManager.isValidName(newName)) {
            player.sendMessage(ChatColor.RED + "Название клана должно быть от 1 до "
                    + ClanManager.MAX_NAME_LENGTH + " символов (буквы, цифры, без пробелов).");
            return;
        }

        if (!manager.renameClan(clan, newName)) {
            player.sendMessage(ChatColor.RED + "Клан с таким названием уже существует.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Клан переименован в \"" + newName + "\".");
    }

    private void handleLeader(Player player, String[] args) {
        Clan clan = manager.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане.");
            return;
        }
        if (!clan.isLeader(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Передать лидерство может только текущий лидер.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Использование: /clan leader <ник>");
            return;
        }

        String targetName = args[1];
        UUID newLeaderId = null;
        for (UUID memberId : clan.getMembers()) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(memberId);
            if (targetName.equalsIgnoreCase(offline.getName())) {
                newLeaderId = memberId;
                break;
            }
        }

        if (newLeaderId == null) {
            player.sendMessage(ChatColor.RED + "Этот игрок не состоит в вашем клане.");
            return;
        }
        if (newLeaderId.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Вы уже лидер клана.");
            return;
        }

        manager.transferLeadership(clan, newLeaderId);
        player.sendMessage(ChatColor.GREEN + "Лидерство передано игроку " + targetName + ".");

        Player newLeaderPlayer = Bukkit.getPlayer(newLeaderId);
        if (newLeaderPlayer != null && newLeaderPlayer.isOnline()) {
            newLeaderPlayer.sendMessage(ChatColor.GOLD + "Вы стали новым лидером клана \"" + clan.getName() + "\"!");
        }
    }

    /**
     * Начисляет очки клану (счётчик ClanScores). Доступно только игрокам
     * с правом luckychests.clan.score (по умолчанию — op), не обязательно
     * лидеру или участнику этого клана, так как это административная команда.
     */
    private void handleScore(Player player, String[] args) {
        if (!player.hasPermission("luckychests.clan.score")) {
            player.sendMessage(ChatColor.RED + "У вас нет прав для начисления очков клану.");
            return;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Использование: /clan score <клан> <количество>");
            return;
        }

        Clan clan = manager.getClanByName(args[1]);
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Клан с таким названием не найден.");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Количество очков должно быть целым числом.");
            return;
        }

        manager.addScore(clan, amount);
        player.sendMessage(ChatColor.GREEN + "Клану \"" + clan.getName() + "\" начислено " + amount
                + " очков. Текущий счёт: " + clan.getScore() + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("leader"))) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return names;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("score")) {
            return manager.getAllClansSortedByScore().stream()
                    .map(Clan::getName)
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}