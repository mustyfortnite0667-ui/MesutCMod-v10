# MesutCMod — Castle audit coordinate fix

Minecraft 1.20.1 Fabric project.

## Changes in this revision
- Players arriving in the Mesut C dimension are teleported to `10.39 96.06 -14.00`.
- Their dimension respawn point is set to block position `10 96 -14`.
- Players and Midas face yaw `106` and pitch `2.9` on arrival/spawn.
- Midas remains at its existing introduction position `21.31 95.50 -7.63`.

Build using Java 17 and `gradlew.bat clean build`.


Midas intro update: Midas now faces the opposite direction (yaw 286°, pitch 2.9°). The introduction begins only when a non-spectator player comes within 4.5 blocks. Cinematic stages are slower: dialogue wait 8 seconds, Mesut reveal wait 5 seconds, lifting 9 seconds, blessing 8 seconds. This archive was not compiled or tested in-game.

## Pixel-art story intro
- Entering `mesutcmod:mesut_christ` opens a 13-slide pixel-art intro.
- Slides auto-advance every 4 seconds; press SPACE to skip/advance.
- Bottom-right text tells the player to press SPACE.
- The last two panels from the original 15-panel storyboard are omitted.
- Undertale's copyrighted `Once Upon a Time` recording is not bundled. If you have permission to use an audio file, put it at `src/main/resources/assets/mesutcmod/sounds/once_upon_a_time.ogg` and rebuild. The sound hook is already configured.
