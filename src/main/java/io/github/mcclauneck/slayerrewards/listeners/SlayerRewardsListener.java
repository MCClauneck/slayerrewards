package io.github.mcclauneck.slayerrewards.listeners;

import io.github.mcclauneck.slayerrewards.common.SlayerRewardsProvider;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.concurrent.Executor;

public class SlayerRewardsListener implements Listener {

    private final Executor executor;
    private final SlayerRewardsProvider provider;

    public SlayerRewardsListener(Executor executor, SlayerRewardsProvider provider) {
        this.executor = executor;
        this.provider = provider;
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        // Check if the killer is a player
        if (event.getEntity().getKiller() instanceof Player) {
            Player killer = event.getEntity().getKiller();
            String playerUuid = killer.getUniqueId().toString();
            
            // Get mob type (e.g., "ZOMBIE", "SKELETON")
            String mobType = event.getEntityType().name();

            // Run the file I/O and DB logic off the main thread
            executor.execute(() -> {
                provider.rewardMoney(playerUuid, mobType);
            });
        }
    }
}
