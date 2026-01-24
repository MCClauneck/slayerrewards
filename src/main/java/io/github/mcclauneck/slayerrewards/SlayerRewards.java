package io.github.mcclauneck.slayerrewards;

import io.github.mcclauneck.slayerrewards.common.SlayerRewardsProvider;
import io.github.mcclauneck.slayerrewards.listeners.SlayerRewardsListener;
import io.github.mcengine.mcextension.api.IMCExtension;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Executor;

public class SlayerRewards implements IMCExtension {

    @Override
    public void onLoad(JavaPlugin plugin, Executor executor) {
        // Initialize the logic provider
        SlayerRewardsProvider provider = new SlayerRewardsProvider(plugin);

        // Register the event listener
        // We pass the provider so the listener knows how to give rewards
        plugin.getServer().getPluginManager().registerEvents(
            new SlayerRewardsListener(executor, provider), 
            plugin
        );
        
        plugin.getLogger().info("[SlayerRewards] Extension loaded successfully.");
    }

    @Override
    public void onDisable(JavaPlugin plugin, Executor executor) {
        plugin.getLogger().info("[SlayerRewards] Extension disabled.");
    }
}
