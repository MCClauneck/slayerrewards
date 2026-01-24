package io.github.mcclauneck.slayerrewards.common;

import io.github.mcclauneck.slayerrewards.api.IReward;
import io.github.mcengine.mceconomy.common.MCEconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class SlayerRewardsProvider implements IReward {

    private final JavaPlugin plugin;
    private final File mobsFolder;

    public SlayerRewardsProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        this.mobsFolder = new File(plugin.getDataFolder(), "extensions/SlayerRewards/mobs");
        
        if (!mobsFolder.exists()) {
            mobsFolder.mkdirs();
        }
    }

    @Override
    public void rewardMoney(String playerUuid, String mobType) {
        // 1. Get the random amount from config
        int amount = getMoney(mobType);
        if (amount <= 0) return; 

        // 2. Get the currency type
        String currency = getCurrency(mobType);

        // 3. Deposit money and chain the message to the result
        MCEconomyProvider.getInstance()
            .addCoin(playerUuid, "PLAYER", currency, amount)
            .thenAccept(success -> {
                if (success) {
                    sendSuccessMessage(playerUuid, amount, currency);
                }
            });
    }

    // New helper method to handle messaging safely
    private void sendSuccessMessage(String uuidString, int amount, String currency) {
        // We look up the player here (Main or Async thread doesn't matter for sendMessage in modern Paper)
        Player player = Bukkit.getPlayer(UUID.fromString(uuidString));
        
        if (player != null && player.isOnline()) {
            // Simple color formatting: Green (+) Amount Currency
            String message = String.format("&a+ %d %s", amount, currency);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    private int getMoney(String mobType) {
        File mobFile = new File(mobsFolder, mobType.toLowerCase() + ".yml");
        if (!mobFile.exists()) return 0;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(mobFile);
        String amountRaw = config.getString("amount", "0");

        try {
            if (amountRaw.contains("-")) {
                String[] parts = amountRaw.split("-");
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                return ThreadLocalRandom.current().nextInt(min, max + 1);
            } else {
                return Integer.parseInt(amountRaw);
            }
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("[SlayerRewards] Invalid amount format in " + mobFile.getName());
            return 0;
        }
    }

    private String getCurrency(String mobType) {
        File mobFile = new File(mobsFolder, mobType.toLowerCase() + ".yml");
        if (!mobFile.exists()) return "coin";

        YamlConfiguration config = YamlConfiguration.loadConfiguration(mobFile);
        String currency = config.getString("currency", "coin");
        
        if (!currency.matches("coin|copper|silver|gold")) {
            return "coin";
        }
        return currency;
    }
}
