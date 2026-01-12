<p align="center">
<img src="icon_big.png" width="512" height="512">
</p>
<h1 align="center">
Valkyrien Air
</h1>
<p align="center">
Ship air/water pockets for Valkyrien Skies 2 (submarines).
</p>

## What it does
- Treats world water as air inside sealed ship interiors while submerged.
- Simulates flooding: ship interiors connected to outside water will fill with water in the shipyard.
- Renders a world water mask so underwater rendering behaves correctly inside ship air pockets.

## Requirements
- Minecraft `1.20.1`
- Valkyrien Skies 2 `2.4.x`
- Loader: Fabric or Forge

## Building
- Windows: `.\gradlew.bat build`
- macOS/Linux: `./gradlew build`

Output jars:
- Fabric: `fabric/build/libs/`
- Forge: `forge/build/libs/`
