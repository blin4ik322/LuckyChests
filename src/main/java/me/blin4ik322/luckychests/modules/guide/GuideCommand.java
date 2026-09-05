package me.blin4ik322.luckychests.modules.guide;

import me.blin4ik322.luckychests.modules.appleboost.AppleBoostModule;
import me.blin4ik322.luckychests.modules.customwither.CustomWitherModule;
import me.blin4ik322.luckychests.modules.expboost.ExpBoostModule;
import me.blin4ik322.luckychests.modules.invest.InvestModule;
import me.blin4ik322.luckychests.modules.witherboost.WitherBoostModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * /guide, /гайд, /help, /помощь — путеводитель по плагину для обычных игроков.
 * Цель модуля — чтобы игрок мог узнать про ВСЕ фичи плагина, не читая
 * исходный код и не спрашивая в чате.
 *
 *   /guide             — общий обзор всех фич плагина
 *   /guide invest       — актуальный ценник /invest (читается напрямую из
 *                         InvestModule.getItemValues(), без дублирования)
 *   /guide totem        — визуальный показ рецепта кастомного тотема (GUI),
 *                         чтобы игрок сам УВИДЕЛ крафт, а не только прочитал о нём
 *   /guide wither       — что такое "Адский Босяк" и какая награда за него
 *   /guide boosts       — усиления опыта/яблок/черепа визера (игрокам — описание
 *                         эффекта, операторам — ещё и точные текущие цифры)
 *   /guide pvp          — как работает боевой PvP-режим
 *   /guide shop         — магазин клана за очки
 *   /guide potion       — Зелье Чутья Врагов
 *   /guide commands     — полный список команд плагина
 *
 * Весь гайд ориентирован строго на игроков: никаких синтаксисов админ-команд
 * не показывается обычным игрокам. Единственное исключение — /guide boosts,
 * где оператору (hasPermission) дополнительно показываются текущие значения
 * множителей — это не инструкция по управлению, а просто более полная справка.
 */
public class GuideCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "invest", "totem", "wither", "boosts", "pvp", "shop", "potion", "lootchests", "meteors", "commands");

    // Слоты 3x3-сетки рецепта внутри 54-слотовой (6 строк) витрины.
    private static final int[] RECIPE_GRID_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int ARROW_SLOT = 23;
    private static final int RESULT_SLOT = 25;

    private final JavaPlugin plugin;
    private final InvestModule investModule;

    public GuideCommand(JavaPlugin plugin, InvestModule investModule) {
        this.plugin = plugin;
        this.investModule = investModule;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendOverview(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "invest":
            case "расценки":
            case "цены":
                sendInvestPrices(sender);
                return true;
            case "totem":
            case "тотем":
                sendTotemRecipe(sender);
                return true;
            case "wither":
            case "визер":
            case "босяк":
                sendWitherInfo(sender);
                return true;
            case "boosts":
            case "бусты":
            case "усиления":
                sendBoostsInfo(sender);
                return true;
            case "pvp":
            case "пвп":
                sendPvpInfo(sender);
                return true;
            case "shop":
            case "магазин":
                sendShopInfo(sender);
                return true;
            case "potion":
            case "зелье":
                sendPotionInfo(sender);
                return true;
            case "lootchests":
            case "хранилища":
            case "сундуки":
                sendLootChestsInfo(sender);
                return true;
            case "meteors":
            case "meteor":
            case "фрикадельки":
            case "фрикаделька":
            case "метеориты":
            case "метеорит":
                sendMeteorsInfo(sender);
                return true;
            case "commands":
            case "команды":
                sendCommandList(sender);
                return true;
            default:
                sendOverview(sender);
                return true;
        }
    }

    private void sendOverview(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Путеводитель по LuckyChests ===");
        sender.sendMessage(ChatColor.YELLOW + "/melt" + ChatColor.GRAY + " — вкл/выкл автоплавку железной и золотой руды");
        sender.sendMessage(ChatColor.YELLOW + "/invest" + ChatColor.GRAY + " — сдать предметы за очки клана (ценник — "
                + ChatColor.YELLOW + "/guide invest" + ChatColor.GRAY + ")");
        sender.sendMessage(ChatColor.YELLOW + "/clan" + ChatColor.GRAY + " — создать/вступить/управлять кланом");
        sender.sendMessage(ChatColor.YELLOW + "/top" + ChatColor.GRAY + " — топ кланов по очкам");
        sender.sendMessage(ChatColor.YELLOW + "/shop" + ChatColor.GRAY + " — магазин за очки клана (подробнее — "
                + ChatColor.YELLOW + "/guide shop" + ChatColor.GRAY + ")");
        sender.sendMessage(ChatColor.YELLOW + "/bounties" + ChatColor.GRAY + " — награды за убийство игроков (растут со стриком жертвы)");
        sender.sendMessage(ChatColor.YELLOW + "Адский Босяк" + ChatColor.GRAY + " — особый визер, встречается вместо обычного"
                + " (подробнее — " + ChatColor.YELLOW + "/guide wither" + ChatColor.GRAY + ")");
        sender.sendMessage(ChatColor.YELLOW + "PvP-режим" + ChatColor.GRAY + " — что происходит в бою с другим игроком (подробнее — "
                + ChatColor.YELLOW + "/guide pvp" + ChatColor.GRAY + ")");
        sender.sendMessage(ChatColor.YELLOW + "Усиления опыта/яблок/черепа визера" + ChatColor.GRAY + " — подробнее — "
                + ChatColor.YELLOW + "/guide boosts");
        sender.sendMessage(ChatColor.YELLOW + "Зелье Чутья Врагов" + ChatColor.GRAY + " — подробнее — "
                + ChatColor.YELLOW + "/guide potion");
        sender.sendMessage(ChatColor.YELLOW + "Сужение барьера мира" + ChatColor.GRAY + " — периодический ивент, запускают операторы");
        sender.sendMessage(ChatColor.YELLOW + "/guide totem" + ChatColor.GRAY + " — посмотреть и увидеть рецепт " + CustomTotem.DISPLAY_NAME);
        sender.sendMessage(ChatColor.YELLOW + "Хранилища" + ChatColor.GRAY + " — случайные сундуки с лутом по всему миру (подробнее — "
                + ChatColor.YELLOW + "/guide lootchests" + ChatColor.GRAY + ")");
        sender.sendMessage(ChatColor.YELLOW + "Фрикадельки" + ChatColor.GRAY + " — метеориты из магазина, сносят всё вокруг (подробнее — "
                + ChatColor.YELLOW + "/guide meteors" + ChatColor.GRAY + ")");
        sender.sendMessage(ChatColor.GRAY + "Полный список команд — " + ChatColor.YELLOW + "/guide commands");
    }

    private void sendWitherInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Адский Босяк ===");
        sender.sendMessage(ChatColor.GRAY + "Особый визер: выделяется разноцветным градиентным именем над головой.");
        sender.sendMessage(ChatColor.GRAY + "Появляется вместо обычного визера при любом способе призыва");
        sender.sendMessage(ChatColor.GRAY + "(яйцо призывания, спавнер, команда, постройка из черепов и т.д.).");
        sender.sendMessage(ChatColor.GRAY + "За убийство твой клан получает " + ChatColor.AQUA
                + CustomWitherModule.KILL_REWARD + ChatColor.GRAY
                + " очков ClanScores (нужно состоять в клане, иначе очки некому начислить).");
    }

    /**
     * Усиления опыта/яблок/черепа визера. Игроку показывается только описание
     * эффекта — какой множитель сейчас стоит, ему знать не нужно, это часть
     * баланса сервера, который настраивают операторы. Оператору дополнительно
     * показываются текущие значения multiplier/enabled для справки.
     */
    private void sendBoostsInfo(CommandSender sender) {
        boolean isAdmin = sender.hasPermission("luckychests.admin");

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Усиления на сервере ===");

        sender.sendMessage(ChatColor.YELLOW + "Опыт" + ChatColor.GRAY
                + " — из всех источников выпадает больше опыта, чем в ванильном Minecraft."
                + (isAdmin ? ChatColor.DARK_GRAY + " [x" + ExpBoostModule.getMultiplier()
                + ", вкл: " + ExpBoostModule.isEnabled() + "]" : ""));

        sender.sendMessage(ChatColor.YELLOW + "Яблоки" + ChatColor.GRAY
                + " — повышенный шанс выпадения яблок при разрушении листвы."
                + (isAdmin ? ChatColor.DARK_GRAY + " [x" + AppleBoostModule.getMultiplier()
                + ", вкл: " + AppleBoostModule.isEnabled() + "]" : ""));

        sender.sendMessage(ChatColor.YELLOW + "Череп визера" + ChatColor.GRAY
                + " — повышенный шанс получить череп при убийстве визера (растёт вместе с Looting)."
                + (isAdmin ? ChatColor.DARK_GRAY + " [x" + WitherBoostModule.getMultiplier()
                + ", вкл: " + WitherBoostModule.isEnabled() + "]" : ""));
    }

    private void sendPvpInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== PvP-режим ===");
        sender.sendMessage(ChatColor.GRAY + "Ударил другого игрока не из своего клана — оба входите в боевой режим:");
        sender.sendMessage(ChatColor.GRAY + "над экраном появляется красный боссбар с отсчётом 10 секунд.");
        sender.sendMessage(ChatColor.GRAY + "Любой новый удар (от любого из двоих) сбрасывает таймер заново.");
        sender.sendMessage(ChatColor.GRAY + "Когда отсчёт закончится — увидишь надпись \"Вы вышли из режима ПВП\".");
    }

    private void sendShopInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Магазин клана ===");
        sender.sendMessage(ChatColor.GRAY + "/shop (/магазин) — открывает магазин, где предметы покупаются");
        sender.sendMessage(ChatColor.GRAY + "за очки твоего клана. Текущий баланс очков виден прямо в заголовке окна.");
        sender.sendMessage(ChatColor.GRAY + "Очки для покупок можно получить через /invest, за убийство");
        sender.sendMessage(ChatColor.GRAY + "Адского Босяка и за победы над другими игроками.");
    }

    private void sendPotionInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Зелье Чутья Врагов ===");
        sender.sendMessage(ChatColor.GRAY + "Особое зелье, которое можно получить от администрации сервера.");
        sender.sendMessage(ChatColor.GRAY + "Выпив его, на 3 минуты в Actionbar видишь расстояние, стрелку");
        sender.sendMessage(ChatColor.GRAY + "направления и оставшееся время до ближайшего врага (игрок не");
        sender.sendMessage(ChatColor.GRAY + "из твоего клана). Компас в инвентаре в это время указывает на него же.");
    }

    private void sendInvestPrices(CommandSender sender) {
        Map<Material, Integer> values = investModule.getItemValues();
        if (values.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Ценник пока пуст — загляните позже.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Ценник /invest:");
        values.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Material, Integer>>comparingInt(Map.Entry::getValue).reversed())
                .forEach(entry -> sender.sendMessage(ChatColor.WHITE + entry.getKey().name() + ChatColor.GRAY + " — "
                        + ChatColor.YELLOW + entry.getValue() + ChatColor.GRAY + " очков/шт."));
    }

    private void sendCommandList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Все команды LuckyChests:");
        sender.sendMessage(ChatColor.YELLOW + "/melt, /плавить" + ChatColor.GRAY + " — автоплавка руды ");
        sender.sendMessage(ChatColor.YELLOW + "/invest, /вложить" + ChatColor.GRAY + " — меню вложений ");
        sender.sendMessage(ChatColor.YELLOW + "/clan, /клан" + ChatColor.GRAY + " — управление кланом ");
        sender.sendMessage(ChatColor.YELLOW + "/top, /топ" + ChatColor.GRAY + " — топ кланов ");
        sender.sendMessage(ChatColor.YELLOW + "/shop, /магазин" + ChatColor.GRAY + " — магазин за очки клана ");
        sender.sendMessage(ChatColor.YELLOW + "/bounties, /награды" + ChatColor.GRAY + " — список наград за игроков ");
        sender.sendMessage(ChatColor.YELLOW + "/guide, /гайд, /help, /помощь" + ChatColor.GRAY
                + " — этот путеводитель ");
        sender.sendMessage(ChatColor.DARK_GRAY + "  подкоманды: " + ChatColor.GRAY
                + "invest, totem, wither, boosts, pvp, shop, potion, lootchests, meteors, commands");
        if (sender.hasPermission("luckychests.expboost")) {
            sender.sendMessage(ChatColor.DARK_GRAY + "/expboost" + ChatColor.GRAY + " — множитель опыта (оператор)");
        }
        if (sender.hasPermission("luckychests.witherchance")) {
            sender.sendMessage(ChatColor.DARK_GRAY + "/witherchance" + ChatColor.GRAY + " — шанс черепа визера (оператор)");
        }
        if (sender.hasPermission("luckychests.event")) {
            sender.sendMessage(ChatColor.DARK_GRAY + "/event, /ивент" + ChatColor.GRAY + " — сужение барьера мира (оператор)");
        }
        if (sender.hasPermission("luckychests.playerbattle")) {
            sender.sendMessage(ChatColor.DARK_GRAY + "/playerbattle" + ChatColor.GRAY + " — настройка наград за PvP (оператор)");
        }
        if (sender.hasPermission("luckychests.clan.score")) {
            sender.sendMessage(ChatColor.DARK_GRAY + "/clan score <клан> <кол-во>" + ChatColor.GRAY + " — начислить очки клану (оператор)");
        }
        if (sender.hasPermission("luckychests.clan.tp")) {
            sender.sendMessage(ChatColor.DARK_GRAY + "/clan tp <клан>" + ChatColor.GRAY + " — телепортировать всех участников клана к себе (оператор)");
        }
        if (sender.hasPermission("luckychests.admin")) {
            sender.sendMessage(ChatColor.DARK_GRAY + "/lootchests" + ChatColor.GRAY + " — управление хранилищами: координаты, дроп, таймер, старт/стоп (оператор)");
            sender.sendMessage(ChatColor.DARK_GRAY + "/applechance" + ChatColor.GRAY + " — множитель шанса яблок (оператор)");
            sender.sendMessage(ChatColor.DARK_GRAY + "/giveradarpotion" + ChatColor.GRAY + " — выдать Зелье Чутья Врагов (оператор)");
            sender.sendMessage(ChatColor.DARK_GRAY + "/announce, /bcast" + ChatColor.GRAY + " — объявления (оператор)");
        }
    }

    private void sendMeteorsInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Фрікадэльки (Метеориты) ===");
        sender.sendMessage(ChatColor.GRAY + "" + ChatColor.ITALIC + "Фрікадэльки — так на сервере называют метеориты.");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Что это:");
        sender.sendMessage(ChatColor.GRAY + "Предмет из " + ChatColor.YELLOW + "/shop" + ChatColor.GRAY
                + ", при использовании которого с неба обрушиваются " + ChatColor.RED + "10–15 метеоритов"
                + ChatColor.GRAY + " — мощный инструмент разрушения и атаки.");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Как работает:");
        sender.sendMessage(ChatColor.GRAY + "• Возьми предмет в руку и используй его (ПКМ).");
        sender.sendMessage(ChatColor.GRAY + "• Через " + ChatColor.RED + "5 секунд"
                + ChatColor.GRAY + " метеориты начнут падать с неба.");
        sender.sendMessage(ChatColor.GRAY + "• Радиус поражения — " + ChatColor.RED + "20 блоков"
                + ChatColor.GRAY + " вокруг точки использования.");
        sender.sendMessage(ChatColor.GRAY + "• Всё в зоне попадания получает урон — игроки, мобы, блоки.");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Где взять:");
        sender.sendMessage(ChatColor.GRAY + "Купить в " + ChatColor.YELLOW + "/shop"
                + ChatColor.GRAY + " за очки клана.");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.RED + "⚠ Используй с умом — метеориты не разбирают своих и чужих.");
    }

    private void sendLootChestsInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Хранилища ===");
        sender.sendMessage(ChatColor.GRAY + "По всему миру в заранее выбранных местах периодически появляются");
        sender.sendMessage(ChatColor.GRAY + "случайные сундуки и бочки — " + ChatColor.YELLOW + "Хранилища" + ChatColor.GRAY + ".");
        sender.sendMessage(ChatColor.GRAY + "Внутри каждого — случайный лут: ресурсы, редкие предметы и не только.");
        sender.sendMessage(ChatColor.GRAY + "Предметы хаотично разбросаны по слотам, а не аккуратно сложены.");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Как это работает:");
        sender.sendMessage(ChatColor.GRAY + "• Одновременно активно несколько хранилищ в разных точках мира.");
        sender.sendMessage(ChatColor.GRAY + "• Хранилище стоит бесконечно, пока его кто-нибудь не откроет.");
        sender.sendMessage(ChatColor.GRAY + "• После первого открытия над ним появляется " + ChatColor.RED + "обратный отсчёт" + ChatColor.GRAY + ".");
        sender.sendMessage(ChatColor.GRAY + "• Когда таймер истечёт — хранилище и весь оставшийся лут исчезнут.");
        sender.sendMessage(ChatColor.GRAY + "• На его месте (или другой свободной точке) тут же появится новое.");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Важно:");
        sender.sendMessage(ChatColor.GRAY + "• Сломать хранилище " + ChatColor.RED + "нельзя" + ChatColor.GRAY + " — оно защищено от разрушения и взрывов.");
        sender.sendMessage(ChatColor.GRAY + "• Поставить свой сундук/бочку на зарезервированное место " + ChatColor.RED + "тоже нельзя" + ChatColor.GRAY + ".");
        sender.sendMessage(ChatColor.GRAY + "• Лут внутри выдаётся по таблице с шансами — не всё выпадет каждый раз.");
    }

    private void sendTotemRecipe(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эту команду может выполнить только игрок.");
            return;
        }
        Player player = (Player) sender;
        player.openInventory(buildTotemRecipeGui());
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Рецепт " + CustomTotem.DISPLAY_NAME
                + ChatColor.GRAY + " открыт — окно только для просмотра, ничего забрать нельзя.");
    }

    /** Витрина 6x9: 3x3-сетка рецепта по центру, стрелка и результат справа. */
    private Inventory buildTotemRecipeGui() {
        Inventory gui = Bukkit.createInventory(new TotemRecipeHolder(), 54,
                ChatColor.DARK_PURPLE + "Рецепт: Тотем Стойкости");

        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, filler);
        }

        List<ItemStack> icons = CustomTotem.recipeIcons();
        for (int i = 0; i < RECIPE_GRID_SLOTS.length; i++) {
            ItemStack icon = icons.get(i);
            if (icon != null) {
                // null = пустая ячейка рецепта — оставляем серый заполнитель как есть,
                // чтобы игрок сразу видел, какие клетки верстака должны остаться пустыми.
                gui.setItem(RECIPE_GRID_SLOTS[i], icon);
            }
        }

        gui.setItem(ARROW_SLOT, namedItem(Material.ARROW, ChatColor.GRAY + "Верстак →"));
        gui.setItem(RESULT_SLOT, CustomTotem.createItem(plugin));

        return gui;
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String lower = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(lower)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}