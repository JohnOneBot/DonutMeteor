package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;

/**
 * Freecam+ module - simple toggle for built-in Freecam.
 * Works with auto-walk, auto-clicker, and other modules.
 * Just locks your position while letting you look around freely.
 */
public class FreecamPlus extends Module {
    private final Freecam freecam;

    public FreecamPlus() {
        super(AddonTemplate.CATEGORY, "freecam-plus", "Simple freecam toggle. Works with auto-walk and auto-clicker.");
        this.freecam = (Freecam) Modules.get().get(Freecam.class);
    }

    @Override
    public void onActivate() {
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
}
