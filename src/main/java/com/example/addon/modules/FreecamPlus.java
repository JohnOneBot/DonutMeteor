package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Freecam+ module - freecam that mines the block you were looking at before freecamming.
 * When you activate freecam, it saves the block you're targeting and keeps mining it
 * even while you look around. Perfect for AFK mining with auto-clicker.
 */
public class FreecamPlus extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Freecam freecam;
    
    private float lockedYaw;
    private float lockedPitch;
    
    // Public static so mixin can access it
    public static BlockPos targetBlock = null;
    public static HitResult storedHit = null;
    public static boolean isFreecamPlusActive = false;
    public static Vec3d storedPlayerPos = null;

    public FreecamPlus() {
        super(AddonTemplate.CATEGORY, "freecam-plus", "Freecam that locks on to your target block. Perfect for AFK mining.");
        this.freecam = (Freecam) Modules.get().get(Freecam.class);
    }

    @Override
    public void onActivate() {
        isFreecamPlusActive = true;
        
        if (mc.player != null && mc.world != null) {
            // Save player's current rotation
            lockedYaw = mc.player.getYaw();
            lockedPitch = mc.player.getPitch();
            storedPlayerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            
            // Find what block the player is looking at
            Vec3d eyePos = mc.player.getEyePos();
            Vec3d lookDir = mc.player.getRotationVec(1.0f);
            Vec3d targetPos = eyePos.add(lookDir.multiply(5.0)); // Standard reach
            
            HitResult hit = mc.world.raycast(
                new RaycastContext(
                    eyePos,
                    targetPos,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
                )
            );
            
            // Save the full hit result (so crosshairTarget can be spoofed)
            storedHit = hit;

            // Save the block position if we're looking at one
            if (hit instanceof BlockHitResult blockHit) {
                targetBlock = blockHit.getBlockPos();
            } else {
                targetBlock = null;
            }
        }
        
        if (freecam != null && !freecam.isActive()) {
            freecam.toggle();
        }
    }

    @Override
    public void onDeactivate() {
        isFreecamPlusActive = false;
        targetBlock = null;
        storedHit = null;
        storedPlayerPos = null;
        
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


