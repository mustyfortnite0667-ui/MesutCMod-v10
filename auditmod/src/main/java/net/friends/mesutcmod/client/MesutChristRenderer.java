package net.friends.mesutcmod.client;

import net.friends.mesutcmod.entity.MesutChristEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

public class MesutChristRenderer extends MobEntityRenderer<MesutChristEntity, BipedEntityModel<MesutChristEntity>> {
    private static final Identifier TEXTURE = new Identifier("mesutcmod", "textures/entity/mesut_christ.png");

    public MesutChristRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER)), 0.58F);
    }

    @Override
    public Identifier getTexture(MesutChristEntity entity) {
        return TEXTURE;
    }
}
