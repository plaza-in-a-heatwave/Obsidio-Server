package com.benberi.cadesim.server.model.player.domain;

import com.benberi.cadesim.server.config.ServerConfiguration;
import com.benberi.cadesim.server.model.player.Player;

public class MoveGenerator {

    /**
     * The player
     */
    private Player player;

    private double cannonGenerationPercentage;

    private double moveGenerationPercentage;

    public MoveGenerator(Player p) {
        this.player = p;
    }

    public void update() {
        if (player.getMoveTokens().getCannons() + player.getMoves().countAllShoots() < player.getVessel().getMaxCannons()) {
            updateCannonGeneration();
        }
        updateMoveGeneration();
    }

    private void updateMoveGeneration() {
        double movesPerSec = player.getJobbersQuality().getMovesPerSec();
        double movesPerSecAffectBilge = movesPerSec - 0.009 * player.getVessel().getBilgePercentage() * movesPerSec;
        double rate = movesPerSecAffectBilge;

        moveGenerationPercentage += rate;
        if (moveGenerationPercentage >= 1) {
            moveGenerationPercentage -= Math.floor(moveGenerationPercentage);
            player.getMoveTokens().moveGenerated();
            player.getPackets().sendTokens();
        }
    }

    private void updateCannonGeneration() {
        double rate = player.getJobbersQuality().getCannonsPerSec();
        cannonGenerationPercentage += rate;
        if (cannonGenerationPercentage >= 1) {
            cannonGenerationPercentage -= Math.floor(cannonGenerationPercentage);
            player.getMoveTokens().addCannons(1);
            player.getPackets().sendTokens();
        }
    }
}
