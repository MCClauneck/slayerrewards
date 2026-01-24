package io.github.mcclauneck.slayerrewards.api;

public interface IReward {
    /**
     * Rewards a player based on the mob type they killed.
     * @param playerUuid The UUID of the killer.
     * @param mobType    The type of the mob (e.g., "ZOMBIE").
     */
    void rewardMoney(String playerUuid, String mobType);
}
