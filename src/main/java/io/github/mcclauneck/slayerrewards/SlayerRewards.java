package io.github.mcclauneck.slayerrewards;

import io.github.mcclauneck.slayerrewards.command.SlayerRewardsCommand;
import io.github.mcclauneck.slayerrewards.common.SlayerRewardsProvider;
import io.github.mcclauneck.slayerrewards.editor.MobDropEditor;
import io.github.mcclauneck.slayerrewards.listeners.SlayerRewardsListener;
import io.github.mcclauneck.slayerrewards.tabcompleter.SlayerRewardsTabCompleter;
import io.github.mcengine.mcextension.api.IMCExtension;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Main entry point for the SlayerRewards extension.
 * <p>
 * This class implements the IMCExtension interface to integrate with
 * the MCEconomy extension system.
 */
public class SlayerRewards implements IMCExtension {

    private SlayerRewardsProvider provider;
    private MobDropEditor editor;

    /**
     * Called when the extension is loaded by MCEconomy.
     * Initializes the provider, editor, listeners, and commands.
     *
     * @param plugin   The host JavaPlugin.
     * @param executor The shared executor service.
     */
    @Override
    public void onLoad(JavaPlugin plugin, Executor executor) {
        this.provider = new SlayerRewardsProvider(plugin);
        this.editor = new MobDropEditor(plugin, provider.getMobsFolder());

        plugin.getServer().getPluginManager().registerEvents(
            new SlayerRewardsListener(executor, provider), 
            plugin
        );
        plugin.getServer().getPluginManager().registerEvents(editor, plugin);

        registerCommand(plugin);
        
        plugin.getLogger().info("[SlayerRewards] Extension loaded successfully.");
    }

    /**
     * Registers the /slayerrewards command dynamically into the Bukkit CommandMap.
     */
    private void registerCommand(JavaPlugin plugin) {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            SlayerRewardsCommand executor = new SlayerRewardsCommand(editor);
            SlayerRewardsTabCompleter tabCompleter = new SlayerRewardsTabCompleter(provider);

            Command cmd = new Command("slayerrewards", "Manage mob drops", "/slayerrewards edit <mob>", Collections.singletonList("slayer")) {
                @Override
                public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                    return executor.onCommand(sender, this, commandLabel, args);
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
                    return tabCompleter.onTabComplete(sender, this, alias, args);
                }
            };

            commandMap.register(plugin.getName(), cmd);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register /slayerrewards command: " + e.getMessage());
        }
    }

    /**
     * Called when the extension is disabled.
     *
     * @param plugin   The host JavaPlugin.
     * @param executor The shared executor service.
     */
    @Override
    public void onDisable(JavaPlugin plugin, Executor executor) {
        this.provider = null;
        this.editor = null;
        plugin.getLogger().info("[SlayerRewards] Extension disabled.");
    }
}
