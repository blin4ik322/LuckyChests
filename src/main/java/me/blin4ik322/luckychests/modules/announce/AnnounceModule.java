package me.blin4ik322.luckychests.modules.announce;

import me.blin4ik322.luckychests.modules.core.PluginModule;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль объявлений для операторов.
 * Вся логика фичи живёт в этой папке (modules/announce):
 *
 *  - AnnounceModule    — точка входа, регистрация команд
 *  - BroadcastCommand   — команды /bcast и /broadcast: объявление в чат
 *  - AnnounceCommand    — команда /announce: title/subtitle + звук колокола
 *
 * Подключается из основного класса через ModuleManager.loadAll(this, new AnnounceModule()).
 */
public final class AnnounceModule implements PluginModule {

    @Override
    public void load(JavaPlugin plugin) {
        BroadcastCommand broadcastCommand = new BroadcastCommand();
        registerCommand(plugin, "broadcast", broadcastCommand);
        registerCommand(plugin, "bcast", broadcastCommand);

        AnnounceCommand announceCommand = new AnnounceCommand();
        registerCommand(plugin, "announce", announceCommand);
    }

    private void registerCommand(JavaPlugin plugin, String name, org.bukkit.command.CommandExecutor executor) {
        if (plugin.getCommand(name) != null) {
            plugin.getCommand(name).setExecutor(executor);
        } else {
            plugin.getLogger().warning("[Announce] команда '" + name
                    + "' не объявлена в plugin.yml — добавьте её в секцию commands.");
        }
    }

    @Override
    public String getName() {
        return "Announce";
    }
}
