package com.chaossmp.chaos;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ChaosSMPChaos extends JavaPlugin implements Listener {

private static final MiniMessage MINI = MiniMessage.miniMessage();

private File playersFile;
private YamlConfiguration playersData;

private final Map<String, Long> killCooldowns = new ConcurrentHashMap<>();

@Override
public void onEnable() {
    saveDefaultConfig();
    loadPlayerData();

    PluginCommand chaosCommand = getCommand("chaos");

    if (chaosCommand == null) {
        getLogger().severe("Команда /chaos не найдена в plugin.yml!");
        getServer().getPluginManager().disablePlugin(this);
        return;
    }

    chaosCommand.setExecutor(this);

    Bukkit.getPluginManager().registerEvents(this, this);

    if (getConfig().getBoolean("settings.auto-save", true)) {
        long interval = Math.max(
                20L,
                getConfig().getLong("settings.save-interval", 60L) * 20L
        );

        Bukkit.getScheduler().runTaskTimer(
                this,
                this::savePlayerData,
                interval,
                interval
        );
    }

    getLogger().info("ChaosSMPChaos успешно запущен!");
}

@Override
public void onDisable() {
    Bukkit.getScheduler().cancelTasks(this);
    savePlayerData();

    getLogger().info("ChaosSMPChaos выключен. Данные сохранены.");
}

private void loadPlayerData() {
    if (!getDataFolder().exists()) {
        getDataFolder().mkdirs();
    }

    playersFile = new File(getDataFolder(), "players.yml");

    if (!playersFile.exists()) {
        try {
            playersFile.createNewFile();
        } catch (IOException exception) {
            getLogger().severe("Не удалось создать players.yml!");
            exception.printStackTrace();
        }
    }

    playersData = YamlConfiguration.loadConfiguration(playersFile);

    getLogger().info(
            "Загружена статистика игроков: "
                    + playersData.getConfigurationSection("players") == null
    );
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

private String path(UUID uuid, String key) {
    return "players." + uuid + "." + key;
}

private int getChaos(UUID uuid) {
    return playersData.getInt(path(uuid, "chaos"), 0);
}

private int getKills(UUID uuid) {
    return playersData.getInt(path(uuid, "kills"), 0);
}

private int getDeaths(UUID uuid) {
    return playersData.getInt(path(uuid, "deaths"), 0);
}

private int getMaxChaos() {
    return Math.max(0, getConfig().getInt("settings.max-chaos", 25));
}

private int getPowerLevel(UUID uuid) {
    int chaos = getChaos(uuid);
    int maxLevel = Math.max(
            1,
            getConfig().getInt("settings.max-power-level", 5)
    );

    int level = 0;

    for (int i = 1; i <= maxLevel; i++) {
        int required = getConfig().getInt(
                "settings.power-levels." + i,
                i * 5
        );

        if (chaos >= required) {
            level = i;
        }
    }

    return level;
}

private void setChaos(UUID uuid, int amount) {
    int safeAmount = Math.max(0, Math.min(amount, getMaxChaos()));

    playersData.set(path(uuid, "chaos"), safeAmount);
}

private void addChaos(UUID uuid, int amount) {
    setChaos(uuid, getChaos(uuid) + amount);
}

private void removeChaos(UUID uuid, int amount) {
    setChaos(uuid, getChaos(uuid) - amount);
}

private void send(CommandSender sender, String path) {
    String prefix = getConfig().getString("messages.prefix", "");
    String message = getConfig().getString(
            path,
            "<red>Сообщение не найдено."
    );

    sender.sendMessage(MINI.deserialize(prefix + message));
}

private void sendRaw(CommandSender sender, String message) {
    String prefix = getConfig().getString("messages.prefix", "");

    sender.sendMessage(MINI.deserialize(prefix + message));
}

private void sendFormatted(
        CommandSender sender,
        String messagePath,
        Map<String, String> placeholders
) {
    String prefix = getConfig().getString("messages.prefix", "");
    String message = getConfig().getString(
            messagePath,
            "<red>Сообщение не найдено."
    );

    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
        message = message.replace(
                "{" + entry.getKey() + "}",
                entry.getValue()
        );
    }

    sender.sendMessage(MINI.deserialize(prefix + message));
}

private boolean hasAdminPermission(CommandSender sender) {
    if (!sender.hasPermission("chaossmpchaos.admin")) {
        send(sender, "messages.no-permission");
        return false;
    }

    return true;
}

private void showStats(CommandSender sender, String name) {
    Player online = Bukkit.getPlayerExact(name);

    OfflinePlayer offline = online != null
            ? online
            : Bukkit.getOfflinePlayer(name);

    UUID uuid = offline.getUniqueId();

    if (online == null && !offline.hasPlayedBefore()) {
        send(sender, "messages.player-not-found");
        return;
    }

    String playerName = offline.getName() == null
            ? name
            : offline.getName();

    sendFormatted(sender, "messages.check", Map.of(
            "player", playerName,
            "chaos", String.valueOf(getChaos(uuid)),
            "max", String.valueOf(getMaxChaos()),
            "power", String.valueOf(getPowerLevel(uuid))
    ));

    sendRaw(
            sender,
            "<gray>Убийства: <green>" + getKills(uuid)
                    + " <gray>| Смерти: <red>" + getDeaths(uuid)
    );
}

private void showTop(CommandSender sender) {
    var section = playersData.getConfigurationSection("players");

    sendRaw(
            sender,
            "<gradient:#5B8CFF:#C084FC><bold>ТОП-10 CHAOS</bold>"
    );

    if (section == null || section.getKeys(false).isEmpty()) {
        sendRaw(sender, "<gray>Пока нет данных.");
        return;
    }

    section.getKeys(false).stream()
            .sorted(Comparator.comparingInt(
                    (String id) -> playersData.getInt(
                            "players." + id + ".chaos"
                    )
            ).reversed())
            .limit(10)
            .forEach(id -> {
                UUID uuid;

                try {
                    uuid = UUID.fromString(id);
                } catch (IllegalArgumentException exception) {
                    return;
                }

                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

                String playerName = player.getName() == null
                        ? id.substring(0, 8)
                        : player.getName();

                sendRaw(
                        sender,
                        "<gray>" + (playersData.getKeys(true).size() > 0 ? "" : "")
                                + "<white>" + playerName
                                + " <dark_gray>— <aqua>"
                                + getChaos(uuid)
                                + " <gray>хаосов"
                );
            });
}

private void executeAdminAction(
        CommandSender sender,
        String action,
        Player target,
        int amount
) {
    UUID uuid = target.getUniqueId();
    int oldLevel = getPowerLevel(uuid);

    switch (action) {
        case "give" -> {
            int before = getChaos(uuid);

            addChaos(uuid, amount);

            int added = getChaos(uuid) - before;

            sendFormatted(sender, "messages.given", Map.of(
                    "player", target.getName(),
                    "amount", String.valueOf(added)
            ));
        }

        case "take" -> {
            int before = getChaos(uuid);

            removeChaos(uuid, amount);

            int removed = before - getChaos(uuid);

            sendFormatted(sender, "messages.taken", Map.of(
                    "player", target.getName(),
                    "amount", String.valueOf(removed)
            ));
        }

        case "set" -> {
            setChaos(uuid, amount);

            sendFormatted(sender, "messages.set", Map.of(
                    "player", target.getName(),
                    "amount", String.valueOf(getChaos(uuid))
            ));
        }

        default -> {
            return;
        }
    }

    int newLevel = getPowerLevel(uuid);

    if (newLevel > oldLevel) {
        sendFormatted(target, "messages.level-up", Map.of(
                "level", String.valueOf(newLevel)
        ));
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
    if (args.length == 0) {
        if (!(sender instanceof Player player)) {
            send(sender, "messages.player-only");
            return true;
        }

        if (!player.hasPermission("chaossmpchaos.use")) {
            send(player, "messages.no-permission");
            return true;
        }

        showStats(player, player.getName());
        return true;
    }

    String subcommand = args[0].toLowerCase();

    switch (subcommand) {
        case "top" -> {
            if (!sender.hasPermission("chaossmpchaos.use")) {
                send(sender, "messages.no-permission");
                return true;
            }

            showTop(sender);
        }

        case "stats", "check" -> {
            if (!sender.hasPermission("chaossmpchaos.use")) {
                send(sender, "messages.no-permission");
                return true;
            }

            if (args.length < 2) {
                send(sender, "messages.invalid-player");
                return true;
            }

            showStats(sender, args[1]);
        }

        case "give", "take", "set" -> {
            if (!hasAdminPermission(sender)) {
                return true;
            }

            if (args.length < 3) {
                send(sender, "messages.invalid-amount");
                return true;
            }

            int amount;

            try {
                amount = Integer.parseInt(args[2]);

                if (amount < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException exception) {
                send(sender, "messages.invalid-amount");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);

            if (target == null) {
                send(sender, "messages.player-not-found");
                return true;
            }

            executeAdminAction(sender, subcommand, target, amount);
        }

        case "reload" -> {
            if (!hasAdminPermission(sender)) {
                return true;
            }

            reloadConfig();
            send(sender, "messages.reloaded");
        }

        default -> sendRaw(
                sender,
                "<red>Использование: /chaos [top|stats|check|give|take|set|reload]"
        );
    }

    return true;
}

@EventHandler
public void onPlayerDeath(PlayerDeathEvent event) {
    Player victim = event.getEntity();
    Player killer = victim.getKiller();

    UUID victimId = victim.getUniqueId();

    playersData.set(path(victimId, "name"), victim.getName());
    playersData.set(
            path(victimId, "deaths"),
            getDeaths(victimId) + 1
    );

    if (killer == null || killer.getUniqueId().equals(victimId)) {
        return;
    }

    UUID killerId = killer.getUniqueId();

    playersData.set(path(killerId, "name"), killer.getName());
    playersData.set(
            path(killerId, "kills"),
            getKills(killerId) + 1
    );

    long now = System.currentTimeMillis();

    String cooldownKey = killerId + ":" + victimId;

    long cooldown = getConfig().getLong(
            "settings.kill-cooldown",
            300
    );

    long lastKill = killCooldowns.getOrDefault(cooldownKey, 0L);

    if (now - lastKill < TimeUnit.SECONDS.toMillis(cooldown)) {
        return;
    }

    killCooldowns.put(cooldownKey, now);

    int reward = Math.max(
            0,
            getConfig().getInt("settings.chaos-per-kill", 1)
    );

    int oldLevel = getPowerLevel(killerId);
    int before = getChaos(killerId);

    addChaos(killerId, reward);

    int received = getChaos(killerId) - before;
    int newLevel = getPowerLevel(killerId);

    if (received > 0) {
        sendFormatted(
                killer,
                "messages.kill-reward",
                Map.of("amount", String.valueOf(received))
        );
    }

    if (newLevel > oldLevel) {
        sendFormatted(killer, "messages.level-up", Map.of(
                "level", String.valueOf(newLevel)
        ));
    }
}
}
