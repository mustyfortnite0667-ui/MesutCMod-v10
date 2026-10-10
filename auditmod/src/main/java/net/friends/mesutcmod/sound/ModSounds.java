package net.friends.mesutcmod.sound;

import net.friends.mesutcmod.MesutCMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
    public static final SoundEvent HOPES_AND_DREAMS = register("hopes_and_dreams");

    private static SoundEvent register(String name) {
        Identifier id = new Identifier(MesutCMod.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
    public static void register() {}
    private ModSounds() {}
}
