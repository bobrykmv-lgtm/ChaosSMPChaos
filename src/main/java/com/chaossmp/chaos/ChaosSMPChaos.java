package com.chaossmp.chaos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.UUID;

public final class ChaosSMPChaos extends JavaPlugin implements Listener, CommandExecutor {

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
            applyChaosBuff(player);
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

    private int getKillReward() {
        return Math.max(0, getConfig().getInt("settings.kill-reward", 1));
    }

    private int getDeathPenalty() {
        return Math.max(0, getConfig().getInt("settings.death-penalty", 1));
    }

    private void setChaos(UUID uuid, String name, int amount) {
        int safeAmount = Math.max(0, amount);

        playersData.set(path(uuid) + ".name", name);
        playersData.set(path(uuid) + ".chaos", safeAmount);
        savePlayerData();

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            applyChaosBuff(player);
        }
    }

    private void applyChaosBuff(Player player) {
        int chaos = getChaos(player.getUniqueId());
        int strengthLevel = chaos / 5;

        player.removePotionEffect(PotionEffectType.STRENGTH);

        if (strengthLevel > 0) {
            int amplifier = strengthLevel - 1;
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 60 * 60 * 24, amplifier, true, false, true));
        }
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
                "<gray>Хаос: <light_purple>" + chaos));
                
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
        applyChaosBuff(event.getPlayer());
        savePlayerData();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        int penalty = getDeathPenalty();
        int reward = getKillReward();

        UUID victimId = victim.getUniqueId();
        int victimBefore = getChaos(victimId);
        
        setChaos(victimId, victim.getName(), Math.max(0, victimBefore - penalty));

        int removed = victimBefore - getChaos(victimId);

        if (removed > 0) {
            sendFormatted(
                    victim,
                    "messages.death-message",
                    killer != null ? killer.getName() : "окружающая среда",
                    removed
            );
        }

        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            UUID killerId = killer.getUniqueId();
            int killerBefore = getChaos(killerId);
            
            setChaos(killerId, killer.getName(), killerBefore + reward);

            int added = getChaos(killerId) - killerBefore;

            if (added > 0) {
                sendFormatted(
                        killer,
                        "messages.kill-message",
                        victim.getName(),
                        added
                );
            }
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
            showStats(player);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("top")) {
            showTop(sender);
            return true;
        }

        // --- /chaos clear all ---
        if (args.length == 2 && args[0].equalsIgnoreCase("clear") && args[1].equalsIgnoreCase("all")) {
            if (!sender.isOp()) {
                sender.sendMessage(MINI.deserialize("<red>У вас нет прав для использования этой команды!"));
                return true;
            }

            ConfigurationSection section = playersData.getConfigurationSection("players");
            if (section != null) {
                for (String uuidStr : section.getKeys(false)) {
                    playersData.set("players." + uuidStr + ".chaos", 0);
                    
                    // Якщо гравець зараз онлайн, оновлюємо йому бафи
                    Player onlinePlayer = Bukkit.getPlayer(UUID.fromString(uuidStr));
                    if (onlinePlayer != null && onlinePlayer.isOnline()) {
                        applyChaosBuff(onlinePlayer);
                        onlinePlayer.sendMessage(MINI.deserialize("<red>Ваши очки хаоса были полностью аннулированы администратором!"));
                    }
                }
                savePlayerData();
            }

            sender.sendMessage(MINI.deserialize("<green>Успешно! Очки хаоса сброшены до 0 для §eyet all §g(всех игроков)§синлайн и офлайн."));
            return true;
        }

        // --- /chaos give та /chaos clear <гравець> <кількість> ---
        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("clear"))) {
            if (!sender.isOp()) {
                sender.sendMessage(MINI.deserialize("<red>У вас нет прав для использования этой команды!"));
                return true;
            }

            String targetName = args[1];
            Player target = Bukkit.getPlayerExact(targetName);
            
            UUID targetUuid = null;
            String foundName = targetName;

            if (target != null) {
                targetUuid = target.getUniqueId();
                foundName = target.getName();
            } else {
                ConfigurationSection section = playersData.getConfigurationSection("players");
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        String nameInConfig = playersData.getString("players." + key + ".name");
                        if (nameInConfig != null && nameInConfig.equalsIgnoreCase(targetName)) {
                            targetUuid = UUID.fromString(key);
                            foundName = nameInConfig;
                            break;
                        }
                    }
                }
            }

            if (targetUuid == null) {
                sender.sendMessage(MINI.deserialize("<red>Игрок " + targetName + " не найден в базе данных!"));
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 0) {
                    sender.sendMessage(MINI.deserialize("<red>Количество не может быть отрицательным!"));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(MINI.deserialize("<red>Укажите корректное число!"));
                return true;
            }

            int currentChaos = getChaos(targetUuid);
            int newChaos;

            if (args[0].equalsIgnoreCase("give")) {
                newChaos = currentChaos + amount;
                setChaos(targetUuid, foundName, newChaos);
                sender.sendMessage(MINI.deserialize("<green>Вы выдали <light_purple>" + amount + " <green>хаоса игроку <yellow>" + foundName + "<green>. Новый баланс: <light_purple>" + newChaos));
                
                if (target != null && target.isOnline()) {
                    target.sendMessage(MINI.deserialize("<green>Вам было начислено <light_purple>" + amount + " <green>хаоса! Текущий баланс: <light_purple>" + newChaos));
                }
            } else {
                newChaos = Math.max(0, currentChaos - amount);
                setChaos(targetUuid, foundName, newChaos);
                sender.sendMessage(MINI.deserialize("<red>Вы забрали <light_purple>" + amount + " <green>хаоса у игрока <yellow>" + foundName + "<green>. Новый баланс: <light_purple>" + newChaos));
                
                if (target != null && target.isOnline()) {
                    target.sendMessage(MINI.deserialize("<red>У вас было списано <light_purple>" + amount + " <green>хаоса! Текущий баланс: <light_purple>" + newChaos));
                }
            }
            return true;
        }

        send(sender, "messages.usage");
        return true;
    }
}
