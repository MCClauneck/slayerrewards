package io.github.mcclauneck.slayerrewards.listeners;

import io.github.mcclauneck.slayerrewards.common.SlayerRewardsProvider;
import org.bukkit.Location; // Import this
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.concurrent.Executor;

/**
 * Listens for EntityDeathEvents to trigger reward distribution.
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
     * Handles the death of an entity. checks if the killer was a player
     * and dispatches the reward logic asynchronously.
     *
     * @param event The EntityDeathEvent.
     */
    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() instanceof Player) {
            Player killer = event.getEntity().getKiller();
            String playerUuid = killer.getUniqueId().toString();
            String mobType = event.getEntityType().name();
            
            // Capture location before async execution
            Location loc = event.getEntity().getLocation();

            executor.execute(() -> {
                // Pass the location to the provider
                provider.rewardMoney(playerUuid, mobType, loc);
            });
        }
    }
}
