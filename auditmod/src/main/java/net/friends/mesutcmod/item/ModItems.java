package net.friends.mesutcmod.item;

import net.friends.mesutcmod.MesutCMod;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    /** Legacy drop kept for compatibility with older worlds. */
    public static final Item KAYA_PARCASI = registerItem("kaya_parcasi", new Item(new Item.Settings().fireproof()));

    // Four story items dropped by the real Ahmet Kaya boss.
    public static final Item KAYA_KIRIK_MUHRU = registerItem("kaya_kirik_muhru", new Item(new Item.Settings().fireproof()));
    public static final Item KARANLIK_CEKIRDEK = registerItem("karanlik_cekirdek", new Item(new Item.Settings().fireproof()));
    public static final Item ESKI_MUHUR_PARCASI = registerItem("eski_muhur_parcasi", new Item(new Item.Settings().fireproof()));
    public static final Item BILINMEYEN_KRISTAL = registerItem("bilinmeyen_kristal", new Item(new Item.Settings().fireproof()));
    public static final Item MESUT_ANAHTARI = registerItem("mesut_anahtari", new Item(new Item.Settings().fireproof().maxCount(1)));

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(MesutCMod.MOD_ID, name), item);
    }

    public static void register() {}
}
