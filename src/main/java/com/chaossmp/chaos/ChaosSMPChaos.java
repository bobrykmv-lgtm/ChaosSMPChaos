package com.chaossmp.chaos;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.UUID;

public final class ChaosSMPChaos extends JavaPlugin {

private static final MiniMessage MINI = MiniMessage.miniMessage();

private File playersFile;
private YamlConfiguration playersData;

@Override
public void onEnable() {
    saveDefaultConfig();
    loadPlayerData();

    if (getCommand("chaos") == null) {
        getLogger().severe("Команда /chaos не найдена в plugin.yml!");
        getServer().getPluginManager().disablePlugin(this);
        return;
    }

    getCommand("chaos").setExecutor(this);
    getLogger().info("ChaosSMPChaos успешно запущен!");
}

@Override
public void onDisable() {
    savePlayerData();
}

private void loadPlayerData() {
    if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
        getLogger().severe("Не удалось создать папку плагина!");
    }

    playersFile = new File(getDataFolder(), "players.yml");
    playersData = YamlConfiguration.loadConfiguration(playersFile);

    getLogger().info("Статистика хаосов загружена.");
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

private String path(UUID uuid) {
    return "players." + uuid;
}

private int getChaos(UUID uuid) {
    return playersData.getInt(path(uuid) + ".chaos", 0);
}

private int getMaxChaos() {
    return Math.max(1, getConfig().getInt("settings.max-chaos", 25));
}

private void setChaos(UUID uuid, String name, int amount) {
    int safeAmount = Math.max(0, Math.min(amount, getMaxChaos()));

    playersData.set(path(uuid) + ".name", name);
    playersData.set(path(uuid) + ".chaos", safeAmount);
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

private boolean hasAdminPermission(CommandSender sender) {
    if (!sender.hasPermission("chaossmpchaos.admin")) {
        send(sender, "messages.no-permission");
        return false;
    }

    return true;
}

private void showTop(CommandSender sender) {
    ConfigurationSection section =
            playersData.getConfigurationSection("players");

    sender.sendMessage(MINI.deserialize(
            "<gradient:#5B8CFF:#C084FC><bold>ТОП-10 CHAOSSMP</bold></gradient>"
    ));

    if (section == null || section.getKeys(false).isEmpty()) {
        sender.sendMessage(MINI.deserialize("<gray>Пока нет игроков в рейтинге."));
        return;
    }

    int[] rank = {0};

    section.getKeys(false).stream()
            .sorted(Comparator.comparingInt(
                    (String id) -> playersData.getInt(
                            "players." + id + ".chaos", 0
                    )
            ).reversed())
            .limit(10)
            .forEach(id -> {
                int chaos = playersData.getInt(
                        "players." + id + ".chaos", 0
                );

                String name = playersData.getString(
                        "players." + id + ".name",
                        "Неизвестный игрок"
                );

                rank[0]++;

                String color = switch (rank[0]) {
                    case 1 -> "<gold>";
                    case 2 -> "<gray>";
                    case 3 -> "<#CD7F32>";
                    default -> "<white>";
                };

                sender.sendMessage(MINI.deserialize(
                        color + "#" + rank[0]
                                + " <white>" + name
                                + " <dark_gray>— <light_purple>"
                                + chaos + " хаосов"
                ));
            });
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

        int chaos = getChaos(player.getUniqueId());

        sender.sendMessage(MINI.deserialize(
                getConfig().getString("messages.prefix", "")
                        + "<gray>Твои хаосы: <light_purple>"
                        + chaos + "<gray>/<aqua>" + getMaxChaos()
        ));
        return true;
    }

    if (args[0].equalsIgnoreCase("top")) {
        showTop(sender);
        return true;
    }

    if (args[0].equalsIgnoreCase("give")
            || args[0].equalsIgnoreCase("take")) {

        if (!hasAdminPermission(sender)) {
            return true;
        }

        if (args.length < 3) {
            send(sender, "messages.usage");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);

        if (target == null) {
            send(sender, "messages.player-not-found");
            return true;
        }

        int amount;

        try {
            amount = Integer.parseInt(args[2]);

            if (amount <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            send(sender, "messages.invalid-amount");
            return true;
        }

        UUID uuid = target.getUniqueId();
        int before = getChaos(uuid);

        if (args[0].equalsIgnoreCase("give")) {
            setChaos(uuid, target.getName(), before + amount);

            int added = getChaos(uuid) - before;

            sendFormatted(
                    sender,
                    "messages.given",
                    target.getName(),
                    added
            );
        } else {
            setChaos(uuid, target.getName(), before - amount);

            int removed = before - getChaos(uuid);

            sendFormatted(
                    sender,
                    "messages.taken",
                    target.getName(),
                    removed
            );
        }

        savePlayerData();
        return true;
    }

    send(sender, "messages.usage");
    return true;
}

}
