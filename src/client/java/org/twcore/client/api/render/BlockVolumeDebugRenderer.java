package org.twcore.client.api.render;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.twcore.api.blockvolume.BlockVolume;

/**
 * 对应用了 {@link BlockVolume} 的方块添加显示方块体信息的效果。
 *
 * <p>该渲染类会在方块的上方渲染出方块体的信息便于调试。客户端没有服务端索引，
 * 方块体信息由方块实体通过 NBT 同步的轻量快照提供（{@link BlockVolume#fromNbt}）。</p>
 *
 * @param <T> 对应的方块实体
 * @since 1.0.3
 */
public interface BlockVolumeDebugRenderer<T extends BlockEntity> {

	/**
	 * 获取用于渲染的文本渲染器。
	 *
	 * @since 1.0.3
	 */
	TextRenderer getTextRenderer();

	/**
	 * 获取方块实体所属的方块体（通常为方块实体同步的结构快照）。
	 *
	 * @param entity 对应的方块实体
	 * @return 方块体；不属于任何方块体时返回 {@code null}
	 * @since 1.0.3
	 */
	@Nullable
	BlockVolume getBlockVolume(T entity);

	/**
	 * 进行其他的调试渲染。
	 *
	 * @param matrices    变换矩阵。注意：此时的矩阵已经被变换到了渲染文字的地方，
	 *                    如果需要一个新的矩阵，请再对矩阵进行一次推送
	 * @since 1.0.3
	 */
	default void otherDebugRender(T entity, BlockVolume volume, float tickDelta, MatrixStack matrices,
								 VertexConsumerProvider vertexConsumers, int light, int overlay) {
	}

	/**
	 * 渲染方块体调试信息。
	 *
	 * @since 1.0.3
	 */
	default void renderDebugInfo(T entity, float tickDelta, MatrixStack matrices,
								VertexConsumerProvider vertexConsumers, int light, int overlay) {
		BlockVolume volume = getBlockVolume(entity);
		if (volume == null) {
			return;
		}

		BlockPos masterPos = volume.masterPos();
		BlockPos currentPos = entity.getPos();

		String masterText = String.format("%d,%d,%d", masterPos.getX(), masterPos.getY(), masterPos.getZ());
		String currentText = String.format("%d,%d,%d", currentPos.getX(), currentPos.getY(), currentPos.getZ());
		String sizeText = String.format("%dx%dx%d", volume.range().width(), volume.range().height(), volume.range().depth());

		boolean isMaster = masterPos.equals(currentPos);
		String masterStatus = isMaster ? "MASTER" : "SLAVE";

		matrices.push();

		try {
			matrices.translate(0.5, 1.2, 0.5);

			float scale = 0.02F;
			matrices.scale(scale, -scale, scale);

			int white = 0xFFFFFFFF;
			int green = 0xFF00FF00;
			int yellow = 0xFFFFFF00;
			int blue = 0xFF0088FF;

			int masterWidth = getTextRenderer().getWidth(masterText);
			int currentWidth = getTextRenderer().getWidth(currentText);
			int sizeWidth = getTextRenderer().getWidth(sizeText);
			int statusWidth = getTextRenderer().getWidth(masterStatus);

			var positionMatrix = matrices.peek().getPositionMatrix();

			// 主方块坐标（蓝色）
			getTextRenderer().draw(
					masterText,
					-masterWidth / 2f, -30,
					blue,
					false,
					positionMatrix,
					vertexConsumers,
					TextRenderer.TextLayerType.POLYGON_OFFSET,
					0,
					light
			);

			// 当前坐标（白色）
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

			// 结构尺寸（黄色）
			getTextRenderer().draw(
					sizeText,
					-sizeWidth / 2f, -10,
					yellow,
					false,
					positionMatrix,
					vertexConsumers,
					TextRenderer.TextLayerType.POLYGON_OFFSET,
					0,
					light
			);

			// 主方块状态（绿色表示主方块，白色表示从方块）
			getTextRenderer().draw(
					masterStatus,
					-statusWidth / 2f, 0,
					isMaster ? green : white,
					false,
					positionMatrix,
					vertexConsumers,
					TextRenderer.TextLayerType.POLYGON_OFFSET,
					0,
					light
			);

			otherDebugRender(entity, volume, tickDelta, matrices, vertexConsumers, light, overlay);
		} finally {
			matrices.pop();
		}
	}
}
