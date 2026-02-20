package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Freecam+ module that allows free camera movement while mining.
 * Toggles the built-in Freecam module and auto-mines the block you're looking at.
 * Suppresses auto-walk and other movement modules so they don't interfere with camera.
 */
public class FreecamPlus extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Freecam freecam;
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    
    private final Setting<Double> reach = sgGeneral.add(new DoubleSetting.Builder()
        .name("reach")
        .description("Mining reach from real player position.")
        .defaultValue(5.0)
        .min(1.0)
        .max(6.0)
        .sliderRange(1.0, 6.0)
        .build()
    );

    public FreecamPlus() {
        super(AddonTemplate.CATEGORY, "freecam-plus", "Freecam with real-position mining. Suppresses auto-walk so it doesn't move your freecam.");
        this.freecam = (Freecam) Modules.get().get(Freecam.class);
    }

    @Override
    public void onActivate() {
        if (freecam != null && !freecam.isActive()) {
            freecam.toggle();
            this.info("Freecam activated for mining.");
        }
        // Ensure attack key is not stuck when enabling
        KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), false);
        releaseMovementKeys();
    }

    @Override
    public void onDeactivate() {
        // Release attack key when module is disabled
        KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), false);
        
        if (freecam != null && freecam.isActive()) {
            freecam.toggle();
            this.info("Freecam deactivated.");
        }
    }

    @EventHandler
    private void onTick(Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        ClientPlayerEntity player = mc.player;
        
        // Suppress auto-walk and other movement modules
        suppressMovementInput();
        
        Vec3d eyePos = player.getEyePos();
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        
        // Calculate look direction vector from yaw and pitch
        Vec3d lookVec = new Vec3d(
            -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)),
            -Math.sin(Math.toRadians(pitch)),
            Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))
        );
        
        // Calculate target position based on reach
        Vec3d targetVec = eyePos.add(lookVec.multiply(reach.get()));
        
        // Perform raycast from eye position to target
        HitResult ray = mc.world.raycast(
            new RaycastContext(eyePos, targetVec, RaycastContext.ShapeType.OUTLINE, 
                RaycastContext.FluidHandling.NONE, player)
        );
        
        // If we hit a block, hold attack to mine it continuously
        if (ray.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) ray;
            BlockPos targetPos = blockHit.getBlockPos();
            
            // Hold attack key and update breaking progress for continuous mining
            KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), true);
            mc.interactionManager.updateBlockBreakingProgress(targetPos, blockHit.getSide());
        } else {
            // Release attack when not targeting a block
            KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), false);
        }
    }

    /**
     * Release all movement keys to prevent them from affecting freecam
     */
    private void releaseMovementKeys() {
        KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.backKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.leftKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.rightKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.sneakKey.getDefaultKey(), false);
    }

    /**
     * Suppress movement input to prevent auto-walk and other modules from moving freecam
     */
    private void suppressMovementInput() {
        // Get current key states
        boolean forward = mc.options.forwardKey.isPressed();
        boolean back = mc.options.backKey.isPressed();
        boolean left = mc.options.leftKey.isPressed();
        boolean right = mc.options.rightKey.isPressed();
        boolean jump = mc.options.jumpKey.isPressed();
        boolean sneak = mc.options.sneakKey.isPressed();
        
        // If any movement key is pressed, override it to prevent auto-walk interference
        if (forward || back || left || right || jump || sneak) {
            releaseMovementKeys();
        }
    }
}
