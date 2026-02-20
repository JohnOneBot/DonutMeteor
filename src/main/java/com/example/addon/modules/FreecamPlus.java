package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;

/**
 * Freecam+ module - freecam that keeps player rotation locked.
 * Allows looking around freely while attacks/mining come from original player direction.
 * Works perfectly with auto-walk, auto-clicker, and amethyst drill.
 */
public class FreecamPlus extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Freecam freecam;
    
    private float lockedYaw;
    private float lockedPitch;

    public FreecamPlus() {
        super(AddonTemplate.CATEGORY, "freecam-plus", "Freecam with locked player rotation. Works with auto-walk and auto-clicker.");
        this.freecam = (Freecam) Modules.get().get(Freecam.class);
    }

    @Override
    public void onActivate() {
        if (mc.player != null) {
            // Save player's current rotation
            lockedYaw = mc.player.getYaw();
            lockedPitch = mc.player.getPitch();
        }
        
        if (freecam != null && !freecam.isActive()) {
            freecam.toggle();
        }
    }

    @Override
    public void onDeactivate() {
        if (freecam != null && freecam.isActive()) {
            freecam.toggle();
        }
    }

    @EventHandler
    private void onTick(Post event) {
        // Keep player rotation locked so attacks come from original direction
        if (mc.player != null) {
            mc.player.setYaw(lockedYaw);
            mc.player.setPitch(lockedPitch);
        }
    }
}

