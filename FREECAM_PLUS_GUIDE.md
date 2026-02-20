# Freecam+ Module Documentation

## Overview
Freecam+ is an advanced camera control module for Meteor Client that allows you to freely move your view around without moving your character's position. Unlike standard Freecam, Freecam+ is specifically designed to prevent block breaking when looking at blocks, making it perfect for surveying mine sites without disrupting your mining operation.

## Features
- **Locked Player Position**: Your character stays completely locked in place
- **Free Camera Movement**: Move and rotate your view freely around your character
- **No Block Breaking**: Blocks won't be destroyed while Freecam+ is active
- **Mouse & Keyboard Control**: 
  - Use the mouse to rotate the camera (left/right and up/down)
  - Use WASD keys to move forward/backward/strafe
  - Use Space/Shift to move up/down vertically
- **Customizable Speed**: Adjust camera movement speed to your preference
- **Optional Sneaking Requirement**: Can require sneaking to activate (disabled by default)

## Settings

### Speed (Default: 0.5)
Controls how fast the camera moves when using keyboard input. Higher values = faster movement.
- Range: 0.1 - 2.0

### Mouse Sensitivity (Default: 1.0)
Controls how responsive the camera is to mouse movement. Higher values = more sensitive.
- Range: 0.1 - 3.0

### Require Sneaking (Default: Off)
When enabled, you must be sneaking (Shift) to use Freecam+. The module will automatically disable when you stop sneaking.

## Usage

1. **Enable the Module**: Toggle "Freecam+" in the modules menu (typically found under the "Example" category)
2. **Control Camera**: 
   - Move mouse left/right to rotate camera horizontally (yaw)
   - Move mouse up/down to rotate camera vertically (pitch)
3. **Move Camera**:
   - Press `W` to move forward (in the direction you're looking)
   - Press `S` to move backward
   - Press `A` to strafe left
   - Press `D` to strafe right
   - Press `Space` to move up
   - Press `Shift` to move down
4. **Adjust Speed**: Use the speed setting to control movement velocity
5. **Disable**: Press the module on/off key or use the module GUI to disable Freecam+

## Key Differences from Standard Freecam

| Feature | Standard Freecam | Freecam+ |
|---------|------------------|----------|
| Player Position | Moves with camera | Locked in place |
| Block Breaking | Breaks blocks when looking | Prevents block breaking |
| Use Case | Free exploration | Safe mining surveying |
| Camera Independence | Camera stays with player | Camera completely independent |

## Technical Details

### How It Works
- When activated, Freecam+ saves your current position and rotation
- Your player position is continuously reset to the saved location each tick
- All player velocity is zeroed out to prevent any movement
- A separate camera state tracks your view rotation and position
- The PlayerInteractionManager mixin prevents block breaking entirely while active
- The module automatically disables when you leave the world

### Compatibility
- **Minecraft Version**: 1.21.11
- **Meteor Client**: 1.21.11-SNAPSHOT
- **Requires**: Fabric Loader 0.18.2+

## Troubleshooting

### Module doesn't appear in menu
- Make sure the addon is properly installed
- Check that the mixin is registered in `addon-template.mixins.json`
- Verify the module is imported and registered in `AddonTemplate.java`

### Camera doesn't move
- Try increasing the Mouse Sensitivity setting
- Check that your mouse isn't locked in chat mode or GUI
- Make sure the speed setting isn't set to 0

### Blocks are still breaking
- This shouldn't happen as the mixin prevents it
- Try toggling the module off and on again
- Restart the game if the issue persists

### Player keeps moving/sliding
- This is normal as the module forces your player to stay in place
- The locking mechanism continuously resets your position

## Console Commands
The module can be toggled via Meteor's command system:
```
/freecam-plus
```

## Future Improvements
Possible enhancements for future versions:
- Add ability to teleport back to the locked position
- Add crosshair customization
- Add flight mode toggle option
- Add position waypoint saving
