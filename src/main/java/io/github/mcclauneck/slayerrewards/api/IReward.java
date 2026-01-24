package io.github.mcclauneck.slayerrewards.api;

import org.bukkit.Location;

/**
 * Interface representing the reward distribution logic.
 * <p>
 * Implementations of this interface handle the calculation and processing
 * of rewards when a mob is slain.
 */
public interface IReward {

    /**
     * Rewards a player based on the mob type they killed.
     *
     * @param playerUuid   The unique identifier of the player to reward.
     * @param mobType      The type of the mob that was killed (e.g., "ZOMBIE").
     * @param dropLocation The location where the mob died, used for visual effects.
     */
    void rewardMoney(String playerUuid, String mobType, Location dropLocation);
}
