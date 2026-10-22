package me.bearlasers.beaconsGiveFlight;

//import com.destroystokyo.paper.event.block.BeaconEffectEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;

public final class BeaconsGiveFlight extends JavaPlugin implements org.bukkit.event.Listener {

    private final HashMap<UUID, Long> beaconActivePlayers = new HashMap<>();
    private BukkitTask expirationTask;

    @Override
    public void onEnable() {
        // Automatically creates a default config.yml if one doesn't exist
        saveDefaultConfig();

        // Register events and the command
        Bukkit.getPluginManager().registerEvents(this, this);

        if (getCommand("beaconsgiveflight") != null) {
            getCommand("beaconsgiveflight").setExecutor(this);
        }

        expirationTask = Bukkit.getScheduler().runTaskTimer(this, this::checkExpirations, 0L, 20L);
        getLogger().info("BeaconsGiveFlight has been enabled!");
    }

    @Override
    public void onDisable() {
        if (expirationTask != null) {
            expirationTask.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (beaconActivePlayers.containsKey(player.getUniqueId()) && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)) {
                player.setAllowFlight(false);
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("beaconsgiveflight")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                // Check for permission (optional, but recommended)
                if (!sender.hasPermission("beaconsgiveflight.reload")) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>You do not have permission to use this command.</red>"));
                    return true;
                }

                //saveDefaultConfig() recreates the config.yml if it was deleted and reload was called
                saveDefaultConfig();
                reloadConfig();

                sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>BeaconsGiveFlight configuration reloaded successfully!</green>"));
                return true;
            } else {
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: /beaconsgiveflight reload</yellow>"));
                return true;
            }
        }
        return false;
    }

    @org.bukkit.event.EventHandler
    public void onPotionEffect(EntityPotionEffectEvent event) {
        org.bukkit.entity.Entity rawEntity = event.getEntity();

        if (!(rawEntity instanceof Player)) {
            return;
        }
        Player player = (Player) rawEntity;

        if (event.getCause() == EntityPotionEffectEvent.Cause.BEACON) {
            UUID uuid = player.getUniqueId();

            beaconActivePlayers.put(uuid, System.currentTimeMillis());

            if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                if (!player.getAllowFlight()) {
                    player.setAllowFlight(true);
                    String msg = getConfig().getString("messages.flight-enabled", "<bold><gradient:#11998e:#38ef7d:#11998e:-1.00>The beacon empowers you with flight.</gradient></bold>");
                    if (!msg.isEmpty()) {
                        Component parsedMessage = MiniMessage.miniMessage().deserialize(msg);
                        player.sendMessage(parsedMessage);
                    }
                }
            }
        }
    }

    private void checkExpirations() {
        long now = System.currentTimeMillis();
        var iterator = beaconActivePlayers.entrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();
            UUID uuid = entry.getKey();
            long lastActive = entry.getValue();

            if (now - lastActive > 4000) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) && player.getAllowFlight()) {
                        player.setAllowFlight(false);
                        player.setFlying(false);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, true, true));
                        String msg = getConfig().getString("messages.flight-expired", "<gray>The beacon is too distant. You lose the ability to fly.");
                        if (!msg.isEmpty()) {
                            Component parsedMessage = MiniMessage.miniMessage().deserialize(msg);
                            player.sendMessage(parsedMessage);
                        }
                    }
                }
                iterator.remove();
            }
        }
    }
}