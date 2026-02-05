package io.github.mcclauneck.slayerrewards.common;

import io.github.mcclauneck.slayerrewards.api.IReward;
import io.github.mcengine.mceconomy.api.enums.CurrencyType;
import io.github.mcengine.mceconomy.common.MCEconomyProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;

import java.io.File;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core implementation of the SlayerRewards system.
 * <p>
 * This class handles reading mob configurations, calculating random money ranges,
 * depositing funds asynchronously via MCEconomy, and spawning visual holograms.
 */
public class SlayerRewardsProvider implements IReward {

    private final JavaPlugin plugin;
    private final File mobsFolder;

    /**
     * Constructs a new provider and initializes the mob configuration folder.
     *
     * @param plugin The host JavaPlugin instance.
     */
    public SlayerRewardsProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        this.mobsFolder = new File(plugin.getDataFolder(), "extensions/SlayerRewards/mobs");
        if (!mobsFolder.exists()) mobsFolder.mkdirs();
    }

    /**
     * Gets the folder containing mob configuration files.
     *
     * @return The mobs folder.
     */
    public File getMobsFolder() {
        return this.mobsFolder;
    }

    /**
     * Processes the reward transaction.
     * <p>
     * This method calculates the amount, deposits it asynchronously, and callbacks
     * to the main thread to spawn a hologram upon success.
     *
     * @param playerUuid   The UUID of the killer.
     * @param mobType      The type of mob killed.
     * @param dropLocation The location to spawn the hologram.
     */
    @Override
    public void rewardMoney(String playerUuid, String mobType, Location dropLocation) {
        int amount = getMoney(mobType);
        if (amount <= 0) return;

        CurrencyType currency = getCurrency(mobType);

        // Updated: Pass CurrencyType enum instead of string
        MCEconomyProvider.getInstance()
            .addCoin(playerUuid, "PLAYER", currency, amount)
            .thenAccept(success -> {
                if (success) {
                    // Jump back to Main Thread to spawn Entity
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        spawnHologram(dropLocation, amount, currency);
                    });
                }
            });
    }

    /**
     * Spawns a temporary TextDisplay entity at the drop location.
     *
     * @param loc    The base location of the mob's death.
     * @param amount   The amount of money gained.
     * @param currency The currency type gained.
     */
    private void spawnHologram(Location loc, int amount, CurrencyType currency) {
        // Offset location slightly up so it doesn't spawn in the ground
        Location spawnLoc = loc.add(0, 1.5, 0);

        // Spawn TextDisplay (1.19.4+ feature, perfect for 1.21)
        TextDisplay display = loc.getWorld().spawn(spawnLoc, TextDisplay.class, text -> {
            text.text(Component.text()
                .append(Component.text("+", NamedTextColor.GREEN))
                .append(Component.text(amount, NamedTextColor.GREEN))
                .append(Component.space())
                .append(Component.text(currency.getName(), NamedTextColor.GREEN))
                .build());
            
            text.setBillboard(Display.Billboard.CENTER); // Always face player
            text.setViewRange(10.0f);
            text.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0)); // Transparent bg
            text.setShadowed(true);
            
            // Optional: Make it slightly larger
            Transformation transformation = text.getTransformation();
            transformation.getScale().set(1.5f, 1.5f, 1.5f);
            text.setTransformation(transformation);
        });

        // Remove after 1.5 seconds (30 ticks)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.remove();
            }
        }, 30L);
    }

    /**
     * Reads the mob configuration file to determine the reward amount.
     * Supports fixed values (e.g., "10") and ranges (e.g., "10-20").
     *
     * @param mobType The name of the mob file to read.
     * @return The calculated money amount, or 0 if invalid.
     */
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
        } catch (NumberFormatException e) { return 0; }
    }

    /**
     * Reads the mob configuration file to determine the currency type.
     *
     * @param mobType The name of the mob file to read.
     * @return The CurrencyType enum. Defaults to COIN.
     */
    private CurrencyType getCurrency(String mobType) {
        File mobFile = new File(mobsFolder, mobType.toLowerCase() + ".yml");
        if (!mobFile.exists()) return CurrencyType.COIN;
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(mobFile);
        String currencyStr = config.getString("currency", "coin");
        
        // Updated: Convert string to Enum
        CurrencyType type = CurrencyType.fromName(currencyStr);
        return type != null ? type : CurrencyType.COIN;
    }
}
