package io.github.mcclauneck.slayerrewards.listeners;

import io.github.mcclauneck.slayerrewards.common.SlayerRewardsProvider;
import io.github.mcclauneck.slayerrewards.editor.util.EditorUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Listens for EntityDeathEvents to trigger reward distribution and custom drops.
 */
public class SlayerRewardsListener implements Listener {

    private final Executor executor;
    private final SlayerRewardsProvider provider;
    // Cache map to store configurations and reduce disk I/O
    private final Map<String, CachedConfig> configCache = new HashMap<>();

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

        // Caching Logic: Check last modified time to prevent unnecessary disk reads
        long currentLastModified = mobFile.lastModified();
        CachedConfig cached = configCache.get(mobType);

        // Reload config and re-deserialize items only if file changed or not cached
        if (cached == null || cached.lastModified() != currentLastModified) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(mobFile);
            boolean cancelDefault = config.getBoolean("cancel_default_drops", false);
            List<CustomDrop> parsedDrops = new ArrayList<>();

            ConfigurationSection section = config.getConfigurationSection("item_drop");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    double chance = section.getDouble(key + ".chance", 100.0);
                    String base64 = section.getString(key + ".metadata");
                    int amount = section.getInt(key + ".amount", 1);

                    if (base64 != null && !base64.isEmpty()) {
                        // Heavy Base64 deserialization happens here, once per file load
                        ItemStack item = EditorUtil.itemStackFromBase64(base64);
                        if (item != null) {
                            parsedDrops.add(new CustomDrop(chance, item, amount));
                        }
                    }
                }
            }
            cached = new CachedConfig(currentLastModified, parsedDrops, cancelDefault);
            configCache.put(mobType, cached);
        }

        // Check if we should cancel vanilla drops
        if (cached.cancelDefault()) {
            event.getDrops().clear();
        }

        // Process pre-cached drop list
        for (CustomDrop drop : cached.drops()) {
            if (ThreadLocalRandom.current().nextDouble() * 100 < drop.chance()) {
                // Must clone the item to avoid modifying the cached instance
                ItemStack item = drop.item().clone();
                item.setAmount(drop.amount());
                event.getDrops().add(item);
            }
        }
    }

    /**
     * Record to hold a single parsed drop.
     */
    private record CustomDrop(double chance, ItemStack item, int amount) {}

    /**
     * Record to hold cached configuration data.
     */
    private record CachedConfig(long lastModified, List<CustomDrop> drops, boolean cancelDefault) {}
}
