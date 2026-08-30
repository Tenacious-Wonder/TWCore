package org.twcore.client.api.render;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.twcore.api.blockpile.CubeBlockPile;
import org.twcore.api.blockpile.CubeBlockPileReference;

/**
 * <p><b>已废弃</b>：方块堆系统已被 {@link org.twcore.api.blockvolume.BlockVolume}（方块体）系列取代，
 * 本类型将在未来版本移除。新代码请迁移到 {@code org.twcore.client.api.render.BlockVolumeDebugRenderer}。</p>
 *
 * 对应用了{@link CubeBlockPile}的方块添加显示方块堆信息的效果
 * <p>该渲染类会在方块的上方渲染出方块堆的信息便于调试</p>
 * @param <T> 对应的方块实体
 */
@Deprecated
public interface CubeBlockPileDebugRenderer<T extends BlockEntity> {

    /**
     * 获取用于渲染的文本渲染器。
     *
     * @return 文本渲染器
     */
    TextRenderer getTextRenderer();

    /**
     * 获取方块的多方块引用
     * @param entity 对应的方块实体
     */
    CubeBlockPileReference getReference(T entity);

    /**
     * 进行其他的调试渲染
     * @param entity 对应的方块实体
     * @param reference 对应的多方块引用
     * @param matrices 变换矩阵，注意，此时的矩阵已经被变换到了渲染文字的地方。
     *                 如果需要一个新的矩阵，请再对矩阵进行一次推送
     */
    default void otherDebugRender(T entity, CubeBlockPileReference reference, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {}

    /**
     * 渲染多方块调试信息。
     */
    default void renderDebugInfo(T entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        // 获取多方块引用
        CubeBlockPileReference multiBlockRef = getReference(entity);
        if (multiBlockRef == null || multiBlockRef.isDisposed()) {
            return;
        }

        // 获取坐标信息
        BlockPos masterPos = multiBlockRef.getMasterWorldPos();
        BlockPos relativePos = multiBlockRef.getRelativePos();
        BlockPos currentPos = entity.getPos();

        // 构建显示的文本
        String masterText = String.format("%d,%d,%d", masterPos.getX(), masterPos.getY(), masterPos.getZ());
        String relativeText = String.format("%d,%d,%d", relativePos.getX(), relativePos.getY(), relativePos.getZ());
        String currentText = String.format("%d,%d,%d", currentPos.getX(), currentPos.getY(), currentPos.getZ());

        // 检查是否为master方块
        boolean isMaster = multiBlockRef.isMasterBlock();
        String masterStatus = isMaster ? "MASTER" : "SLAVE";

        // 检查方块堆完整性
        boolean isIntact = multiBlockRef.checkIntegrity();
        String integrityStatus = isIntact ? "INTACT" : "BROKEN";

        matrices.push();

        try {
            // 将坐标系移动到方块中心上方
            matrices.translate(0.5, 1.2, 0.5);

            // 缩放文本，使其不会太大
            float scale = 0.02F;
            matrices.scale(scale, -scale, scale);

            // 渲染文本
            int white = 0xFFFFFFFF;  // 添加Alpha通道
            int green = 0xFF00FF00;
            int red = 0xFFFF0000;
            int yellow = 0xFFFFFF00;
            int blue = 0xFF0088FF;

            // 计算文本宽度用于居中
            int masterWidth = getTextRenderer().getWidth(masterText);
            int relativeWidth = getTextRenderer().getWidth(relativeText);
            int currentWidth = getTextRenderer().getWidth(currentText);
            int statusWidth = getTextRenderer().getWidth(masterStatus);
            int integrityWidth = getTextRenderer().getWidth(integrityStatus);

            int maxWidth = Math.max(Math.max(masterWidth, relativeWidth),
                    Math.max(currentWidth, Math.max(statusWidth, integrityWidth)));

            // 获取位置矩阵
            var positionMatrix = matrices.peek().getPositionMatrix();

            // 渲染主方块坐标（蓝色）
            getTextRenderer().draw(
                    masterText,
                    -masterWidth / 2f, -40,
                    blue,
                    false,
                    positionMatrix,
                    vertexConsumers,
                    TextRenderer.TextLayerType.POLYGON_OFFSET,
                    0,
                    light
            );

            // 渲染相对坐标（黄色）
            getTextRenderer().draw(
                    relativeText,
                    -relativeWidth / 2f, -30,
                    yellow,
                    false,
                    positionMatrix,
                    vertexConsumers,
                    TextRenderer.TextLayerType.POLYGON_OFFSET,
                    1,
                    light
            );

            // 渲染当前坐标（白色）
            getTextRenderer().draw(
                    currentText,
                    -currentWidth / 2f, -20,
                    white,
                    false,
                    positionMatrix,
                    vertexConsumers,
                    TextRenderer.TextLayerType.POLYGON_OFFSET,
                    0,
                    light
            );

            // 渲染主方块状态（绿色表示主方块，白色表示从方块）
            int masterColor = isMaster ? green : white;
            getTextRenderer().draw(
                    masterStatus,
                    -statusWidth / 2f, -10,
                    masterColor,
                    false,
                    positionMatrix,
                    vertexConsumers,
                    TextRenderer.TextLayerType.POLYGON_OFFSET,
                    0,
                    light
            );

            // 渲染完整性状态（绿色表示完整，红色表示损坏）
            int integrityColor = isIntact ? green : red;
            getTextRenderer().draw(
                    integrityStatus,
                    -integrityWidth / 2f, 0,
                    integrityColor,
                    false,
                    positionMatrix,
                    vertexConsumers,
                    TextRenderer.TextLayerType.POLYGON_OFFSET,
                    0,
                    light
            );

            otherDebugRender(entity, multiBlockRef, tickDelta, matrices, vertexConsumers, light, overlay);
        } finally {
            matrices.pop();
        }
    }
}
