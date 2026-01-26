package io.github.mcclauneck.slayerrewards.tabcompleter;

import io.github.mcclauneck.slayerrewards.common.SlayerRewardsProvider;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles tab completion for the /slayerrewards command.
 * <p>
 * Provides suggestions for subcommands ("edit") and dynamic mob names
 * based on all valid Minecraft entity types.
 * </p>
 */
public class SlayerRewardsTabCompleter implements TabCompleter {

    private final SlayerRewardsProvider provider;
    private final List<String> allEntityTypes;

    /**
     * Constructs a new tab completer.
     *
     * @param provider The provider instance used to access the mobs folder.
     */
    public SlayerRewardsTabCompleter(SlayerRewardsProvider provider) {
        this.provider = provider;
        this.allEntityTypes = Arrays.stream(EntityType.values())
                .filter(EntityType::isAlive)
                .map(type -> type.name().toLowerCase())
                .collect(Collectors.toList());
    }

    /**
     * Generates tab completion suggestions.
     *
     * @param sender   The source of the command.
     * @param command  The command being completed.
     * @param alias    The alias used.
     * @param args     The arguments provided so far.
     * @return A list of suggestions, or an empty list if none.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Collections.singletonList("edit"), completions);
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            StringUtil.copyPartialMatches(args[1], allEntityTypes, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}
