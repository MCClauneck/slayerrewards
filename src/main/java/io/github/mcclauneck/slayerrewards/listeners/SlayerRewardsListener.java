package io.github.mcclauneck.slayerrewards.listeners;

import io.github.mcclauneck.slayerrewards.common.SlayerRewardsProvider;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Listens for EntityDeathEvents to trigger reward distribution and custom drops.
 */
public class SlayerRewardsListener implements Listener {

    private final Executor executor;
    private final SlayerRewardsProvider provider;

    /**
     * Creates a new listener instance.
     *
     * @param executor The executor for handling tasks off the main thread.
     * @param provider The provider logic for processing rewards.
     */
    public SlayerRewardsListener(Executor executor, SlayerRewardsProvider provider) {
        this.executor = executor;
        this.provider = provider;
    }

    /**
     * Handles the death of an entity.
     * <p>
     * 1. Synchronously handles custom item drops and cancelling default drops.
     * 2. Asynchronously handles money calculation and database transactions.
     * </p>
     *
     * @param event The EntityDeathEvent.
     */
    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() instanceof Player) {
            Player killer = event.getEntity().getKiller();
            String playerUuid = killer.getUniqueId().toString();
            String mobType = event.getEntityType().name();

            // 1. Handle Custom Drops (Must be Sync)
            handleCustomDrops(event, mobType);

            // 2. Handle Money Reward (Async)
            // Capture location before async execution to avoid race conditions
            Location loc = event.getEntity().getLocation();

            executor.execute(() -> {
                provider.rewardMoney(playerUuid, mobType, loc);
            });
        }
    }

    /**
     * Processes custom item drops defined in the mob's YAML file.
     *
     * @param event   The death event (to modify drops).
     * @param mobType The type of mob killed.
     */
    private void handleCustomDrops(EntityDeathEvent event, String mobType) {
        File mobFile = new File(provider.getMobsFolder(), mobType.toLowerCase() + ".yml");
        if (!mobFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(mobFile);

        // Check if we should cancel vanilla drops
        if (config.getBoolean("cancel_default_drops", false)) {
            event.getDrops().clear();
        }

        // Process custom drop list
        ConfigurationSection section = config.getConfigurationSection("item_drop");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            // Check chance
            double chance = section.getDouble(key + ".chance", 100.0);
            if (ThreadLocalRandom.current().nextDouble() * 100 < chance) {
                
                ItemStack item = section.getItemStack(key + ".metadata");
                if (item != null) {
                    // Add to the drops list naturally
                    event.getDrops().add(item);
                }
            }
        }
    }
}
