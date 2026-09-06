package me.blin4ik322.luckychests;

import me.blin4ik322.luckychests.modules.announce.AnnounceModule;
import me.blin4ik322.luckychests.modules.appleboost.AppleBoostModule;
import me.blin4ik322.luckychests.modules.automelter.AutoMelterModule;
import me.blin4ik322.luckychests.modules.clans.ClanCommand;
import me.blin4ik322.luckychests.modules.clans.ClanManager;
import me.blin4ik322.luckychests.modules.clans.ClanTopCommand;
import me.blin4ik322.luckychests.modules.core.ModuleManager;
import me.blin4ik322.luckychests.modules.customwither.CustomWitherModule;
import me.blin4ik322.luckychests.modules.dragonboss.DragonBossModule;
import me.blin4ik322.luckychests.modules.enemypotion.EnemyPotionCommand;
import me.blin4ik322.luckychests.modules.enemypotion.EnemyPotionListener;
import me.blin4ik322.luckychests.modules.enemypotion.EnemyPotionModule;
import me.blin4ik322.luckychests.modules.expboost.ExpBoostModule;
import me.blin4ik322.luckychests.modules.guide.GuideModule;
import me.blin4ik322.luckychests.modules.invest.InvestModule;
import me.blin4ik322.luckychests.modules.lootchests.LootChestsCommand;
import me.blin4ik322.luckychests.modules.lootchests.LootChestsListener;
import me.blin4ik322.luckychests.modules.lootchests.LootChestsModule;
import me.blin4ik322.luckychests.modules.meteorite.MeteoriteCommand;
import me.blin4ik322.luckychests.modules.meteorite.MeteoriteListener;
import me.blin4ik322.luckychests.modules.meteorite.MeteoriteModule;
import me.blin4ik322.luckychests.modules.playersbattle.PlayerBattleModule;
import me.blin4ik322.luckychests.modules.pvpmode.PvpCombatListener;
import me.blin4ik322.luckychests.modules.pvpmode.PvpModeModule;
import me.blin4ik322.luckychests.modules.pvpmode.PvpTimerManager;
import me.blin4ik322.luckychests.modules.shop.ShopCommand;
import me.blin4ik322.luckychests.modules.shop.ShopListener;
import me.blin4ik322.luckychests.modules.shop.ShopModule;
import me.blin4ik322.luckychests.modules.witherboost.WitherBoostModule;
import me.blin4ik322.luckychests.modules.WorldBorderTimer;
import me.blin4ik322.luckychests.modules.worldbordertimer.EventCommand;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuckyChests extends JavaPlugin {

    private ClanManager clanManager;
    private ShopModule shopModule;
    private WorldBorderTimer worldBorderTimer;
    private PlayerBattleModule playerBattleModule;
    private EnemyPotionModule enemyPotionModule;
    private MeteoriteModule meteoriteModule;
    private LootChestsModule lootChestsModule;
    private DragonBossModule dragonBossModule;


    @Override
    public void onEnable() {
        // ── Модули без зависимостей ──────────────────────────────────────────
        ModuleManager.loadAll(this,
                new AutoMelterModule(),
                new ExpBoostModule(),
                new WitherBoostModule(),
                new AnnounceModule(),
                new AppleBoostModule()
        );

        // ── Кланы ───────────────────────────────────────────────────────────
        // ClanManager хранит кланы и их очки (ClanScores) в clans.yml
        // и переживает перезагрузку/перезапуск сервера (сохранение — сразу после
        // каждого изменения, см. ClanManager). Очки клана удаляются только вместе
        // с кланом, по команде /clan disband.
        clanManager = new ClanManager(this);
        clanManager.load();

        ClanCommand clanCommand = new ClanCommand(clanManager);
        getCommand("clan").setExecutor(clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);
        getCommand("top").setExecutor(new ClanTopCommand(clanManager));

        // ── PVP-режим ────────────────────────────────────────────────────────
        // Подключается ПОСЛЕ ClanManager, чтобы передать его в слушатель
        // (проверка «в одном клане» отсекает союзников).
        PvpTimerManager pvpTimerManager = new PvpTimerManager(this);
        PvpCombatListener pvpListener = new PvpCombatListener(pvpTimerManager);
        pvpListener.setClanManager(clanManager);          // передаём ClanManager
        getServer().getPluginManager().registerEvents(pvpListener, this);

        // ── Награды за игроков ───────────────────────────────────────────────
        // Динамическая награда клану за PvP-убийство: растёт вместе с надбавкой
        // жертвы (+20% за игрока, +15% за визера, +10% за дракона) и сбрасывается
        // после её смерти.
        // Включается/настраивается командой /playerbattle, список — /bounties.
        // Подключается ДО модулей боссов: им нужен его PlayerBattleManager,
        // чтобы начислять убийце надбавку к собственной награде за голову.
        playerBattleModule = new PlayerBattleModule(this, clanManager);
        playerBattleModule.enable();

        // ── Кастомный визер ──────────────────────────────────────────────────
        // Градиентное имя и награда клану за убийство (+ надбавка убийце).
        new CustomWitherModule(this, clanManager, playerBattleModule.getManager()).enable();

        // ── Дракон Края ──────────────────────────────────────────────────────
        // Награда клану за убийство дракона, не чаще раза в час на весь сервер.
        dragonBossModule = new DragonBossModule(this, clanManager, playerBattleModule.getManager());
        dragonBossModule.enable();

        // ── WorldBorderTimer ─────────────────────────────────────────────────
        // Сужение барьера мира по /event start|stop.
        worldBorderTimer = new WorldBorderTimer(this);
        worldBorderTimer.enable();
        EventCommand eventCommand = new EventCommand(worldBorderTimer);
        getCommand("event").setExecutor(eventCommand);
        getCommand("event").setTabCompleter(eventCommand);

        // ── Вложения ─────────────────────────────────────────────────────────
        // Виртуальный сундук /invest (/вложить), очки идут в клан игрока.
        InvestModule investModule = new InvestModule(this, clanManager);
        investModule.enable();

        // ── Путеводитель ─────────────────────────────────────────────────────
        // /guide (/гайд, /help, /помощь) — общий обзор фич плагина,
        // актуальный ценник /invest (читает его напрямую из InvestModule),
        // визуальный показ рецепта тотема (GUI) и настоящая регистрация этого рецепта
        // на верстаке сервера. Подключается после InvestModule.
        new GuideModule(this, investModule).enable();


        enemyPotionModule = new EnemyPotionModule(this, clanManager);
        Bukkit.getPluginManager().registerEvents(new EnemyPotionListener(enemyPotionModule), this);

        EnemyPotionCommand radarCommand = new EnemyPotionCommand(enemyPotionModule);
        getCommand("giveradarpotion").setExecutor(radarCommand);
        getCommand("giveradarpotion").setTabCompleter(radarCommand);

        enemyPotionModule.startTask(this);

        // ── Метеоритный дождь ────────────────────────────────────────────────
        // Создаём здесь, ДО ShopModule — магазину нужен готовый meteoriteModule,
        // чтобы при покупке выдавать настоящую "Фрiкадэльку", а не «пустышку».
        meteoriteModule = new MeteoriteModule(this);
        getServer().getPluginManager().registerEvents(new MeteoriteListener(meteoriteModule), this);
        getCommand("givemeteorite").setExecutor(new MeteoriteCommand(meteoriteModule));

        // ── Магазин ──────────────────────────────────────────────────────────
        // /shop (/магазин, /store) — тратим очки клана на предметы.
        // Создаётся здесь, а не полем класса, чтобы clanManager, enemyPotionModule
        // и meteoriteModule были уже не null (иначе NPE внутри ShopModule/ShopListener).
        // enemyPotionModule нужен магазину, чтобы при покупке Зелья Чутья Врагов
        // выдавать настоящий предмет модуля, а не «пустышку»; meteoriteModule — то же
        // самое для "Фрiкадэльки".
        shopModule = new ShopModule(this, clanManager, enemyPotionModule, meteoriteModule);

        getServer().getPluginManager().registerEvents(new ShopListener(this, shopModule), this);

        if (getCommand("shop") != null) {
            getCommand("shop").setExecutor(new ShopCommand(shopModule));
        } else {
            getLogger().warning("[Shop] команда 'shop' не объявлена в plugin.yml — добавьте её.");
        }


        // Поле, а не локальная переменная: в onDisable() нужно вызвать
        // lootChestsModule.shutdown() — остановить отсчёты, убрать летающий текст
        // и сохранить стоящие хранилища, чтобы после перезапуска они не остались
        // в мире "ничьими" (без таймера и без защиты от разрушения).
        lootChestsModule = new LootChestsModule(this);
        getServer().getPluginManager().registerEvents(new LootChestsListener(lootChestsModule), this);
        LootChestsCommand lootChestsCommand = new LootChestsCommand(lootChestsModule);
        getCommand("lootchests").setExecutor(lootChestsCommand);
        getCommand("lootchests").setTabCompleter(lootChestsCommand);

        getLogger().info("LuckyChests успешно запущен!");
    }

    @Override
    public void onDisable() {
        if (clanManager != null) {
            // Дополнительное сохранение на всякий случай — ClanManager и так
            // сохраняет данные после каждого изменения, но лишним не будет.
            clanManager.save();
        }
        if (playerBattleModule != null) {
            playerBattleModule.save();
        }
        if (enemyPotionModule != null) {
            enemyPotionModule.stopTask(); // сам таск.cancel() + activeRadars.clear()
        }
        if (lootChestsModule != null) {
            lootChestsModule.shutdown();
        }
        if (dragonBossModule != null) {
            dragonBossModule.shutdown();
        }
        getLogger().info("LuckyChests выключен.");
    }
}