package net.friends.mesutcmod.client;

import net.friends.mesutcmod.entity.MidasEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

public class MidasRenderer extends MobEntityRenderer<MidasEntity, BipedEntityModel<MidasEntity>> {
    private static final Identifier TEXTURE = new Identifier("mesutcmod", "textures/entity/midas.png");

    public MidasRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER)), 0.55F);
    }

    @Override
    public Identifier getTexture(MidasEntity entity) {
        return TEXTURE;
    }
}
