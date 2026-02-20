package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.PlayerUpdateEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

/**
 * Freecam+ module that allows free camera movement while locking player position.
 * Unlike normal freecam, this won't break blocks and keeps the player locked in place.
 * Perfect for surveying mining positions without disrupting your mining operation.
 */
public class FreecamPlus extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Camera movement speed multiplier.")
        .defaultValue(0.5)
        .range(0.1, 2.0)
        .sliderRange(0.1, 2.0)
        .build()
    );

    private final Setting<Double> mouseSensitivity = sgGeneral.add(new DoubleSetting.Builder()
        .name("mouse-sensitivity")
        .description("How fast the camera rotates with mouse movement.")
        .defaultValue(1.0)
        .range(0.1, 3.0)
        .sliderRange(0.1, 3.0)
        .build()
    );

    private final Setting<Boolean> requireSneaking = sgGeneral.add(new BoolSetting.Builder()
        .name("require-sneaking")
        .description("Only allow freecam while sneaking.")
        .defaultValue(false)
        .build()
    );

    // State
    private Vec3d savedPosition;
    private float savedYaw;
    private float savedPitch;
    private Vec3d cameraPos;
    private float cameraYaw;
    private float cameraPitch;
    private double lastMouseX;
    private double lastMouseY;

    public FreecamPlus() {
        super(AddonTemplate.CATEGORY, "freecam-plus", "Free camera that locks your mining position without breaking blocks.");
    }

    @Override
    public void onDeactivate() {
        // Restore player state
        if (mc.player != null) {
            mc.player.setPosition(savedPosition);
            mc.player.setYaw(savedYaw);
            mc.player.setPitch(savedPitch);
        }
        cameraPos = null;
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        // Save current state
        savedPosition = mc.player.getPos();
        savedYaw = mc.player.getYaw();
        savedPitch = mc.player.getPitch();

        // Initialize camera
        cameraPos = savedPosition.add(0, mc.player.getEyeHeight(mc.player.getPose()), 0);
        cameraYaw = savedYaw;
        cameraPitch = savedPitch;
        
        lastMouseX = mc.mouse.getX();
        lastMouseY = mc.mouse.getY();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            if (isActive()) toggle();
            return;
        }

        // Check if we should disable
        if (requireSneaking.get() && !mc.player.isSneaking()) {
            toggle();
            return;
        }

        // Lock player position and prevent any movement
        mc.player.setPosition(savedPosition);
        mc.player.setVelocity(Vec3d.ZERO);
        mc.player.fallDistance = 0;
    }

    @EventHandler
    private void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null) return;

        // Handle mouse-based camera rotation
        double mouseX = mc.mouse.getX();
        double mouseY = mc.mouse.getY();
        
        double deltaX = (mouseX - lastMouseX) * 0.15 * mouseSensitivity.get();
        double deltaY = (mouseY - lastMouseY) * 0.15 * mouseSensitivity.get();
        
        cameraYaw -= (float) deltaX;
        cameraPitch += (float) deltaY;
        
        // Clamp pitch
        if (cameraPitch > 90.0f) cameraPitch = 90.0f;
        if (cameraPitch < -90.0f) cameraPitch = -90.0f;
        
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        // Handle keyboard-based camera movement
        handleCameraInput();

        // Update player rotation to match camera (for appearance)
        mc.player.setYaw(cameraYaw);
        mc.player.setPitch(cameraPitch);

        // Prevent any velocity
        mc.player.setVelocity(Vec3d.ZERO);
    }

    private void handleCameraInput() {
        if (mc.player == null) return;

        // Get camera direction vectors
        double forward = 0;
        double strafe = 0;
        double vertical = 0;

        // Handle input
        if (mc.options.forwardKey.isPressed()) forward += 1;
        if (mc.options.backKey.isPressed()) forward -= 1;
        if (mc.options.rightKey.isPressed()) strafe += 1;
        if (mc.options.leftKey.isPressed()) strafe -= 1;
        if (mc.options.jumpKey.isPressed()) vertical += 1;
        if (mc.options.sneakKey.isPressed()) vertical -= 1;

        if (forward == 0 && strafe == 0 && vertical == 0) return;

        // Calculate movement direction based on camera rotation
        float yawRad = (float) Math.toRadians(cameraYaw);
        float pitchRad = (float) Math.toRadians(cameraPitch);

        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);

        double moveX = (strafe * cos + forward * sin * cosPitch) * speed.get();
        double moveY = (vertical - forward * sinPitch) * speed.get();
        double moveZ = (forward * cos * cosPitch - strafe * sin) * speed.get();

        cameraPos = cameraPos.add(moveX, moveY, moveZ);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        // Disable when leaving world
        if (isActive()) {
            toggle();
        }
    }
}
