package org.twcore.environment;

import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.noise.NoiseRouter;
import org.twcore.api.environment.TimePoint;

/**
 * {@link org.twcore.api.environment.ClimateQuery} 的实现。
 * <p>
 * 根据世界噪声、季节相位和高度衰减，计算现实温度与湿度。
 */
public class ClimateQueryImpl {

    // 噪声理论范围
    private static final float TEMP_NOISE_MIN = -2.22f;
    private static final float TEMP_NOISE_MAX = 2.22f;
    private static final float HUM_NOISE_MIN = -1.69f;
    private static final float HUM_NOISE_MAX = 1.69f;

    // 现实温度映射目标
    private static final float REAL_TEMP_MIN = -20.0f;
    private static final float REAL_TEMP_MAX = 50.0f;

    // 季节振幅
    private static final float SEASON_TEMP_AMPLITUDE = 0.5f;
    private static final float SEASON_HUM_AMPLITUDE = 0.15f;

    // 高度修正
    private static final float HEIGHT_TEMP_DECAY = -0.0065f;
    private static final float HEIGHT_HUM_DECAY = -0.005f;
    private static final int SEA_LEVEL = 63;

    /**
     * 计算实时温度。
     */
    public static float getTemperature(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return 0.0f;

        float baseNoise = sampleTemperatureNoise(serverWorld, pos);
        TimePoint timePoint = TimePoint.fromWorld(serverWorld);
        float seasonOffset = computeSeasonTemperatureOffset(timePoint);
        float heightOffset = (pos.getY() - SEA_LEVEL) * HEIGHT_TEMP_DECAY;

        return mapToRealTemperature(baseNoise + seasonOffset + heightOffset);
    }

    /**
     * 计算实时湿度。
     */
    public static float getHumidity(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return 50.0f;

        float baseNoise = sampleHumidityNoise(serverWorld, pos);
        TimePoint timePoint = TimePoint.fromWorld(serverWorld);
        float seasonOffset = computeSeasonHumidityOffset(timePoint);
        float heightOffset = (pos.getY() - SEA_LEVEL) * HEIGHT_HUM_DECAY;

        return mapToRealHumidity(baseNoise + seasonOffset + heightOffset);
    }

    public static float getRawTemperatureNoise(World world, BlockPos pos) {
        return (world instanceof ServerWorld sw) ? sampleTemperatureNoise(sw, pos) : 0.0f;
    }

    public static float getRawHumidityNoise(World world, BlockPos pos) {
        return (world instanceof ServerWorld sw) ? sampleHumidityNoise(sw, pos) : 0.0f;
    }

    // ---- 噪声采样 ----
    private static float sampleTemperatureNoise(ServerWorld world, BlockPos pos) {
        NoiseRouter router = getNoiseRouter(world);
        if (router == null) return 0.0f;
        var noisePos = new DensityFunction.UnblendedNoisePos(pos.getX(), pos.getY(), pos.getZ());
        return (float) router.temperature().sample(noisePos);
    }

    private static float sampleHumidityNoise(ServerWorld world, BlockPos pos) {
        NoiseRouter router = getNoiseRouter(world);
        if (router == null) return 0.0f;
        var noisePos = new DensityFunction.UnblendedNoisePos(pos.getX(), pos.getY(), pos.getZ());
        return (float) router.vegetation().sample(noisePos);
    }

    private static NoiseRouter getNoiseRouter(ServerWorld world) {
        ServerChunkManager chunkManager = world.getChunkManager();
        NoiseConfig noiseConfig = chunkManager.getNoiseConfig();
        return noiseConfig.getNoiseRouter();
    }

    // ---- 季节偏移（使用 TimePoint 相位） ----
    private static float computeSeasonTemperatureOffset(TimePoint timePoint) {
        return (float) (-Math.cos(timePoint.getSeasonPhase()) * SEASON_TEMP_AMPLITUDE);
    }

    private static float computeSeasonHumidityOffset(TimePoint timePoint) {
        return (float) (-Math.cos(timePoint.getSeasonPhase()) * SEASON_HUM_AMPLITUDE);
    }

    // ---- 映射到现实值 ----
    private static float mapToRealTemperature(float noise) {
        return (noise - TEMP_NOISE_MIN) / (TEMP_NOISE_MAX - TEMP_NOISE_MIN)
                * (REAL_TEMP_MAX - REAL_TEMP_MIN) + REAL_TEMP_MIN;
    }

    private static float mapToRealHumidity(float noise) {
        return (noise - HUM_NOISE_MIN) / (HUM_NOISE_MAX - HUM_NOISE_MIN) * 100.0f;
    }
}