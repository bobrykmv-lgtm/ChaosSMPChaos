package com.chaossmp.chaos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.UUID;

public final class ChaosSMPChaos extends JavaPlugin implements Listener {

private static final MiniMessage MINI = MiniMessage.miniMessage();

private File playersFile;
private YamlConfiguration playersData;

@Override
public void onEnable() {
    saveDefaultConfig();

    if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
        getLogger().severe("Не удалось создать папку плагина!");
        getServer().getPluginManager().disablePlugin(this);
        return;
    }

    playersFile = new File(getDataFolder(), "players.yml");
    playersData = YamlConfiguration.loadConfiguration(playersFile);

    if (getCommand("chaos") == null) {
        getLogger().severe("Команда /chaos не найдена в plugin.yml!");
        getServer().getPluginManager().disablePlugin(this);
        return;
    }

    getCommand("chaos").setExecutor(this);
    Bukkit.getPluginManager().registerEvents(this, this);

    for (Player player : Bukkit.getOnlinePlayers()) {
        updatePlayerName(player);
    }

    savePlayerData();
    getLogger().info("ChaosSMPChaos успешно запущен!");
}

@Override
public void onDisable() {
    savePlayerData();
}

private String path(UUID uuid) {
    return "players." + uuid;
}

private int getChaos(UUID uuid) {
    return Math.max(0, playersData.getInt(path(uuid) + ".chaos", 0));
}

private int getMaxChaos() {
    return Math.max(0, getConfig().getInt("settings.max-chaos", 25));
}

private int getKillReward() {
    return Math.max(0, getConfig().getInt("settings.kill-reward", 1));
}

private int getDeathPenalty() {
    return Math.max(0, getConfig().getInt("settings.death-penalty", 1));
}

private void setChaos(UUID uuid, String name, long amount) {
    int safeAmount = (int) Math.max(0, Math.min(amount, getMaxChaos()));

    playersData.set(path(uuid) + ".name", name);
    playersData.set(path(uuid) + ".chaos", safeAmount);
}

private void updatePlayerName(Player player) {
    UUID uuid = player.getUniqueId();

    playersData.set(path(uuid) + ".name", player.getName());

    if (!playersData.contains(path(uuid) + ".chaos")) {
        playersData.set(path(uuid) + ".chaos", 0);
    }
}

private void savePlayerData() {
    if (playersData == null || playersFile == null) {
        return;
    }

    try {
        playersData.save(playersFile);
    } catch (IOException exception) {
        getLogger().severe("Не удалось сохранить players.yml!");
        exception.printStackTrace();
    }
}

private void send(CommandSender sender, String messagePath) {
    String prefix = getConfig().getString("messages.prefix", "");
    String message = getConfig().getString(
            messagePath,
            "<red>Сообщение не найдено."
    );

    sender.sendMessage(MINI.deserialize(prefix + message));
}

private void sendFormatted(
        CommandSender sender,
        String messagePath,
        String player,
        int amount
) {
    String prefix = getConfig().getString("messages.prefix", "");
    String message = getConfig().getString(
            messagePath,
            "<red>Сообщение не найдено."
    );

    message = message
            .replace("{player}", player)
            .replace("{amount}", String.valueOf(amount));

    sender.sendMessage(MINI.deserialize(prefix + message));
}

private String getName(String id) {
    return playersData.getString(
            "players." + id + ".name",
            "Неизвестный игрок"
    );
}

private int getRank(UUID target) {
    ConfigurationSection section =
            playersData.getConfigurationSection("players");

    if (section == null) {
        return 0;
    }

    var entries = section.getKeys(false).stream()
            .sorted(Comparator
                    .comparingInt((String id) ->
                            playersData.getInt("players." + id + ".chaos", 0))
                    .reversed()
                    .thenComparing(this::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();

    int index = entries.indexOf(target.toString());

    return index < 0 ? entries.size() + 1 : index + 1;
}

private void showStats(Player player) {
    UUID uuid = player.getUniqueId();

    int chaos = getChaos(uuid);
    int rank = getRank(uuid);

    String prefix = getConfig().getString("messages.prefix", "");

    player.sendMessage(Component.empty());
    player.sendMessage(MINI.deserialize(prefix +
            getConfig().getString("messages.stats-title",
                    "<bold>ТВОЯ СТАТИСТИКА</bold>")));
    player.sendMessage(MINI.deserialize(prefix +
            getConfig().getString("messages.stats-chaos",
                    "<gray>Хаосы: {chaos}/{max}")
                    .replace("{chaos}", String.valueOf(chaos))
                    .replace("{max}", String.valueOf(getMaxChaos()))));
    player.sendMessage(MINI.deserialize(prefix +
            getConfig().getString("messages.stats-rank",
                    "<gray>Место: #{rank}")
                    .replace("{rank}", String.valueOf(rank))));
    player.sendMessage(Component.empty());
}

private void showTop(CommandSender sender) {
    ConfigurationSection section =
            playersData.getConfigurationSection("players");

    String prefix = getConfig().getString("messages.prefix", "");

    sender.sendMessage(Component.empty());
    sender.sendMessage(MINI.deserialize(prefix +
            getConfig().getString("messages.top-title",
                    "<bold>ТОП-10 CHAOSSMP</bold>")));

    if (section == null || section.getKeys(false).isEmpty()) {
        send(sender, "messages.top-empty");
        return;
    }

    var entries = section.getKeys(false).stream()
            .sorted(Comparator
                    .comparingInt((String id) ->
                            playersData.getInt("players." + id + ".chaos", 0))
                    .reversed()
                    .thenComparing(this::getName, String.CASE_INSENSITIVE_ORDER))
            .limit(10)
            .toList();

    int rank = 0;

    for (String id : entries) {
        rank++;

        int chaos = playersData.getInt("players." + id + ".chaos", 0);
        String name = getName(id);

        String color = switch (rank) {
            case 1 -> "<gold>";
            case 2 -> "<gray>";
            case 3 -> "<#CD7F32>";
            default -> "<white>";
        };

        Component line = MINI.deserialize(
                prefix + color + "#" + rank + " "
        ).append(Component.text(name))
         .append(MINI.deserialize(
                " <dark_gray>— <light_purple>" + chaos + " хаос"
         ));

        sender.sendMessage(line);
    }

    sender.sendMessage(Component.empty());
}

@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    updatePlayerName(event.getPlayer());
    savePlayerData();
}

@EventHandler
public void onPlayerDeath(PlayerDeathEvent event) {
    Player victim = event.getEntity();
    Player killer = victim.getKiller();

    if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
        return;
    }

    int penalty = getDeathPenalty();
    int reward = getKillReward();

    UUID victimId = victim.getUniqueId();
    UUID killerId = killer.getUniqueId();

    int victimBefore = getChaos(victimId);
    setChaos(victimId, victim.getName(), victimBefore - penalty);

    int removed = victimBefore - getChaos(victimId);

    if (removed > 0) {
        sendFormatted(
                victim,
                "messages.death-message",
                killer.getName(),
                removed
        );
    }

    int killerBefore = getChaos(killerId);
    setChaos(killerId, killer.getName(), (long) killerBefore + reward);

    int added = getChaos(killerId) - killerBefore;

    if (added > 0) {
        sendFormatted(
                killer,
                "messages.kill-message",
                victim.getName(),
                added
        );
    }

    savePlayerData();
}

@Override
public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
) {
    if (!(sender instanceof Player player)) {
        send(sender, "messages.player-only");
        return true;
    }

    if (args.length == 0) {
        showStats(player);
        return true;
    }

    if (args.length == 1 && args[0].equalsIgnoreCase("top")) {
        showTop(sender);
        return true;
    }

    send(sender, "messages.usage");
    return true;
}

}
