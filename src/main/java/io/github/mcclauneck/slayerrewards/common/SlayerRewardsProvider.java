package io.github.mcclauneck.slayerrewards.common;

import io.github.mcclauneck.slayerrewards.api.IReward;
import io.github.mcengine.mceconomy.common.MCEconomyProvider;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.ThreadLocalRandom;

public class SlayerRewardsProvider implements IReward {

    private final JavaPlugin plugin;
    private final File mobsFolder;

    public SlayerRewardsProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        // Sets path to: plugins/MCEconomy/extensions/SlayerRewards/mobs/
        // Assuming the host plugin is MCEconomy
        this.mobsFolder = new File(plugin.getDataFolder(), "extensions/SlayerRewards/mobs");
        
        if (!mobsFolder.exists()) {
            mobsFolder.mkdirs();
        }
    }

    @Override
    public void rewardMoney(String playerUuid, String mobType) {
        // 1. Get the random amount from config
        int amount = getMoney(mobType);
        if (amount <= 0) return; // No reward configured or error

        // 2. Get the currency type (coin, gold, etc.)
        String currency = getCurrency(mobType);

        // 3. Deposit the money using MCEconomyProvider
        // We use "PLAYER" as the standard account type
        MCEconomyProvider.getInstance().addCoin(playerUuid, "PLAYER", currency, amount);
    }

    private int getMoney(String mobType) {
        File mobFile = new File(mobsFolder, mobType.toLowerCase() + ".yml");
        if (!mobFile.exists()) return 0;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(mobFile);
        String amountRaw = config.getString("amount", "0");

        try {
            if (amountRaw.contains("-")) {
                // Handle Range: "10-20"
                String[] parts = amountRaw.split("-");
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                return ThreadLocalRandom.current().nextInt(min, max + 1);
            } else {
                // Handle Single: "15"
                return Integer.parseInt(amountRaw);
            }
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("[SlayerRewards] Invalid amount format in " + mobFile.getName());
            return 0;
        }
    }

    private String getCurrency(String mobType) {
        File mobFile = new File(mobsFolder, mobType.toLowerCase() + ".yml");
        if (!mobFile.exists()) return "coin"; // Default fallback

        YamlConfiguration config = YamlConfiguration.loadConfiguration(mobFile);
        String currency = config.getString("currency", "coin");
        
        // Validation check (optional, based on your comment)
        if (!currency.matches("coin|copper|silver|gold")) {
            return "coin";
        }
        
        return currency;
    }
}
