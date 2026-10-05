package org.twcore.client.render.armor;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.DyeableArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 静态盔甲渲染的实现：持有两份模型与纹饰图集，把一件盔甲按槽位分层画出来。
 *
 * <p>
 * 对外的门面在 {@link org.twcore.client.api.render.armor.StaticArmorRenderer}，
 * 这里放绘制细节与贴图命名规则，调用者不必关心。
 * </p>
 */
public final class StaticArmorDrawing {
    /** 贴图路径缓存：同一路径每次算出的编号固定。 */
    private static final Map<String, Identifier> TEXTURE_CACHE = new HashMap<>();

    /** 内层盔甲模型，护腿使用。 */
    private final BipedEntityModel<LivingEntity> innerModel;

    /** 外层盔甲模型，头盔、胸甲与靴子使用。 */
    private final BipedEntityModel<LivingEntity> outerModel;

    /** 纹饰图集，画纹饰时按材料与图案取 sprite。 */
    private final SpriteAtlasTexture trimsAtlas;

    public StaticArmorDrawing(BipedEntityModel<LivingEntity> innerModel,
            BipedEntityModel<LivingEntity> outerModel, SpriteAtlasTexture trimsAtlas) {
        this.innerModel = innerModel;
        this.outerModel = outerModel;
        this.trimsAtlas = trimsAtlas;
    }

    /**
     * @param slot 装备槽位
     * @return 该槽位对应的模型：护腿用内层，其余用外层
     */
    public BipedEntityModel<LivingEntity> modelFor(EquipmentSlot slot) {
        return slot == EquipmentSlot.LEGS ? this.innerModel : this.outerModel;
    }

    /**
     * 按槽位设置模型的部件可见性：只保留该部位盔甲覆盖的部件。
     *
     * @param model 要调整的模型
     * @param slot  装备槽位
     */
    public static void showOnly(BipedEntityModel<?> model, EquipmentSlot slot) {
        model.setVisible(false);

        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {
            }
        }
    }

    public void draw(ItemStack stack, EquipmentSlot slot, @Nullable DynamicRegistryManager registries,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        showOnly(modelFor(slot), slot);

        drawParts(stack, slot, registries, matrices, vertexConsumers, light);
    }

    public void drawParts(ItemStack stack, EquipmentSlot slot, @Nullable DynamicRegistryManager registries,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armor) || armor.getSlotType() != slot) {
            return;
        }

        boolean innerLayer = slot == EquipmentSlot.LEGS;
        BipedEntityModel<LivingEntity> model = modelFor(slot);

        // 皮革甲按原版画两层：先铺染色后的底色，再叠一层原图的覆盖层
        if (armor instanceof DyeableArmorItem dyeable) {
            int color = dyeable.getColor(stack);

            drawLayer(model, matrices, vertexConsumers, light, textureOf(armor, innerLayer, null),
                    (color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
            drawLayer(model, matrices, vertexConsumers, light, textureOf(armor, innerLayer, "overlay"),
                    1.0F, 1.0F, 1.0F);
        } else {
            drawLayer(model, matrices, vertexConsumers, light, textureOf(armor, innerLayer, null),
                    1.0F, 1.0F, 1.0F);
        }

        // 纹饰不是普通贴图，而是纹饰图集里的 sprite，按材料与图案取；
        // 纹饰记录存在物品 NBT 里、由数据包注册表定义，所以解析需要调用者给出注册表
        if (registries != null) {
            ArmorTrim.getTrim(registries, stack).ifPresent(trim ->
                    drawTrim(model, matrices, vertexConsumers, light, armor.getMaterial(), trim, innerLayer));
        }

        if (stack.hasGlint()) {
            model.render(matrices, vertexConsumers.getBuffer(RenderLayer.getArmorEntityGlint()),
                    light, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /** 用一张贴图把模型按给定颜色画一遍。 */
    private static void drawLayer(BipedEntityModel<LivingEntity> model, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, Identifier texture,
            float red, float green, float blue) {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getArmorCutoutNoCull(texture));
        model.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, red, green, blue, 1.0F);
    }

    /** 从纹饰图集取该材料与图案的 sprite，再把模型画一遍。 */
    private void drawTrim(BipedEntityModel<LivingEntity> model, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, ArmorMaterial material, ArmorTrim trim,
            boolean innerLayer) {
        Sprite sprite = this.trimsAtlas.getSprite(
                innerLayer ? trim.getLeggingsModelId(material) : trim.getGenericModelId(material));
        VertexConsumer buffer = sprite.getTextureSpecificVertexConsumer(
                vertexConsumers.getBuffer(TexturedRenderLayers.getArmorTrims()));

        model.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 按原版的命名规则算出一件盔甲某层的贴图。
     *
     * @param armor      防具物品
     * @param innerLayer 护腿使用第二层贴图
     * @param overlay    取覆盖层时传 {@code "overlay"}，否则传 {@code null}
     * @return 贴图编号
     */
    private static Identifier textureOf(ArmorItem armor, boolean innerLayer, @Nullable String overlay) {
        String material = armor.getMaterial().getName();
        String suffix = "_layer_" + (innerLayer ? 2 : 1) + (overlay == null ? "" : "_" + overlay) + ".png";

        // 材质名带命名空间时（其它模组注册的盔甲材料），贴图也落在那个命名空间下
        int separator = material.indexOf(Identifier.NAMESPACE_SEPARATOR);

        if (separator >= 0) {
            String namespace = material.substring(0, separator);
            String path = material.substring(separator + 1);

            return TEXTURE_CACHE.computeIfAbsent(
                    namespace + ":textures/models/armor/" + path + suffix, Identifier::new);
        }

        return TEXTURE_CACHE.computeIfAbsent("textures/models/armor/" + material + suffix, Identifier::new);
    }
}
