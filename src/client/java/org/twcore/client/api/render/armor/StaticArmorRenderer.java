package org.twcore.client.api.render.armor;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import org.jetbrains.annotations.Nullable;

import org.twcore.client.render.armor.StaticArmorDrawing;

/**
 * <h1>静态盔甲渲染</h1>
 *
 * <p>
 * 把一件盔甲按槽位画到给定的矩阵上，不依赖实体：模型与纹饰图集由调用者提供，画在什么位置、
 * 什么朝向也由调用者决定。适用于台面展示、界面预览这类盔甲并不穿在身上的场合。
 * </p>
 *
 * <h2>使用前提</h2>
 * <ul>
 *     <li><b>模型自备</b>：绘制会改动模型的部件可见性，因此每个使用方都应单独造一份，
 *         且贴图尺寸必须取 64×32 —— 盔甲贴图的高是 32，不是玩家皮肤的 64×64，写错会让 UV 整体错位：
 *         <pre>{@code TexturedModelData.of(ArmorEntityModel.getModelData(new Dilation(1.0F)), 64, 32).createModel()}</pre>
 *         也不要使用 {@code EntityModelLoader} 里共享的那一份，那会波及玩家身上的盔甲；</li>
 *     <li><b>内外两层</b>：护腿用内层模型（膨胀 0.5）配第二层贴图，头盔、胸甲与靴子用外层模型（膨胀 1.0）；</li>
 *     <li><b>纹饰图集</b>：由 {@code BakedModelManager.getAtlas(TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE)} 取得。</li>
 * </ul>
 *
 * <h2>接入方式</h2>
 * <pre>{@code
 * StaticArmorRenderer renderer = new StaticArmorRenderer(innerModel, outerModel, trimsAtlas);
 *
 * matrices.push();
 * matrices.translate(0.5D, 0.0D, 0.5D);
 * renderer.draw(stack, EquipmentSlot.CHEST, world.getRegistryManager(), matrices, vertexConsumers, light);
 * matrices.pop();
 * }</pre>
 *
 * <p>
 * {@link #draw} 会先按槽位挑出该部位覆盖的部件；要把整套盔甲一起画出来，改用
 * {@link #drawParts}，并自己用 {@code model.setVisible(true)} 让所有部件可见。
 * </p>
 *
 * @see org.twcore.client.render.armor.StaticArmorDrawing
 * @since 1.0.5
 */
public final class StaticArmorRenderer {
    /** 绘制细节都在实现里，本类只做转发。 */
    private final StaticArmorDrawing drawing;

    /**
     * 创建一个静态盔甲渲染器。
     *
     * @param innerModel 内层盔甲模型（膨胀 0.5），供护腿使用
     * @param outerModel 外层盔甲模型（膨胀 1.0），供头盔、胸甲与靴子使用
     * @param trimsAtlas 纹饰图集
     * @since 1.0.5
     */
    public StaticArmorRenderer(BipedEntityModel<LivingEntity> innerModel,
            BipedEntityModel<LivingEntity> outerModel, SpriteAtlasTexture trimsAtlas) {
        this.drawing = new StaticArmorDrawing(innerModel, outerModel, trimsAtlas);
    }

    /**
     * 取某个槽位要用的模型：护腿用内层，其余用外层。
     *
     * @param slot 装备槽位
     * @return 该槽位对应的模型实例
     * @since 1.0.5
     */
    public BipedEntityModel<LivingEntity> modelFor(EquipmentSlot slot) {
        return this.drawing.modelFor(slot);
    }

    /**
     * 按槽位设置模型的部件可见性：只保留该部位盔甲覆盖的部件。
     *
     * <p>
     * 同一张盔甲贴图里铺着头盔、胸甲与靴子三套纹理，不做这一步会把它们一起画出来。
     * </p>
     *
     * @param model 要调整的模型
     * @param slot  装备槽位
     * @since 1.0.5
     */
    public static void showOnly(BipedEntityModel<?> model, EquipmentSlot slot) {
        StaticArmorDrawing.showOnly(model, slot);
    }

    /**
     * 画出一件盔甲：先按槽位调整部件可见性，再绘制。
     *
     * @param stack           要画的盔甲物品；空堆或与该槽位不匹配时什么都不画
     * @param slot            装备槽位
     * @param registries      解析纹饰用的注册表；传 {@code null} 表示不画纹饰
     * @param matrices        变换矩阵栈
     * @param vertexConsumers 顶点消费者提供器
     * @param light           光照等级
     * @since 1.0.5
     */
    public void draw(ItemStack stack, EquipmentSlot slot, @Nullable DynamicRegistryManager registries,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        this.drawing.draw(stack, slot, registries, matrices, vertexConsumers, light);
    }

    /**
     * 画出一件盔甲，不动部件可见性——部件的取舍由调用者自己决定。
     *
     * @param stack           要画的盔甲物品；空堆或与该槽位不匹配时什么都不画
     * @param slot            装备槽位
     * @param registries      解析纹饰用的注册表；传 {@code null} 表示不画纹饰
     * @param matrices        变换矩阵栈
     * @param vertexConsumers 顶点消费者提供器
     * @param light           光照等级
     * @since 1.0.5
     */
    public void drawParts(ItemStack stack, EquipmentSlot slot, @Nullable DynamicRegistryManager registries,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        this.drawing.drawParts(stack, slot, registries, matrices, vertexConsumers, light);
    }
}
