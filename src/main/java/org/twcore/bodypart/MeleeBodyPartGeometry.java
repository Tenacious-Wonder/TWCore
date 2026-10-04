package org.twcore.bodypart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import org.twcore.api.bodypart.BodyPart;

/**
 * 近战攻击命中部位的推定。
 *
 * <p>近战攻击没有可以求交点的飞行轨迹，命中部位改为按攻击者的出手点与受击者的站位推定：攻击者
 * 够得着的部位按距离分配权重，越近越重，再从中抽取一个。出手点取攻击者身高的一定比例处，因此
 * 体型不同的生物会攻击到不同的高度。</p>
 */
public final class MeleeBodyPartGeometry {

    /**
     * 出手点相对攻击者身高的比例。调高则头与胸更容易被命中，调低则腿脚更容易被命中。
     */
    static final double REFERENCE_HEIGHT_RATIO = 0.55;

    /**
     * 横向容差：出手点横向偏出玩家身体这么多以外，远侧的肩膀就够不着。
     */
    private static final double SIDE_REACH = 0.3;

    /**
     * 前后容差：出手点偏到身体正面或背面这么深以外，另一面的躯干就够不着。
     */
    private static final double DEPTH_REACH = 0.15;

    /**
     * 距离平方上摊的零头，避免出手点正好落在某块的正中心时权重除出无穷大。
     */
    private static final double DISTANCE_EPSILON = 0.01;

    /**
     * 各部位从哪一边才够得着。
     *
     * <p>没有登记的部位从任何一边都够得着：后脑勺也是头，从背后一样能砍到腿。</p>
     */
    private static final Map<BodyPart, Facing> FACINGS = Map.of(
            BodyPart.LEFT_SHOULDER, new Facing(-1.0, 0.0, SIDE_REACH),
            BodyPart.RIGHT_SHOULDER, new Facing(1.0, 0.0, SIDE_REACH),
            BodyPart.ABDOMEN, new Facing(0.0, 1.0, DEPTH_REACH),
            BodyPart.CHEST, new Facing(0.0, 1.0, DEPTH_REACH),
            BodyPart.BACK, new Facing(0.0, -1.0, DEPTH_REACH));

    private MeleeBodyPartGeometry() {
    }

    /**
     * 推定一次近战攻击命中的部位。
     *
     * <p>按各部位的权重从玩家自己的随机源抽取一个，因此结果既随机又与站位相关：离出手点越近、
     * 朝着攻击者的一侧越容易抽中。</p>
     *
     * @param player   被攻击的玩家
     * @param attacker 下手的一方
     * @return 命中的部位
     * @since 1.0.5
     */
    public static BodyPart pick(PlayerEntity player, LivingEntity attacker) {
        List<PartWeight> weights = weightsFor(player, referencePointOf(attacker));

        double total = 0.0;
        for (PartWeight weight : weights) {
            total += weight.weight();
        }

        double roll = player.getRandom().nextDouble() * total;
        for (PartWeight weight : weights) {
            roll -= weight.weight();
            if (roll < 0.0) {
                return weight.part();
            }
        }

        // 正常走不到这里：上面的减法必然在总和耗尽之前命中某一项。
        // 只有浮点零头把 roll 留在边界上时才会落到这里，兜给最后一项。
        return weights.get(weights.size() - 1).part();
    }

    /**
     * 求攻击者的出手点：横向与前后取他的碰撞箱中心，高度取身高乘上 {@link #REFERENCE_HEIGHT_RATIO}。
     *
     * <p>{@code getPos()} 给出的是脚底中心，因此只有高度需要额外加上去。用攻击者当前的身高而不是
     * 固定数值，是为了让体型自己决定攻击高度。</p>
     *
     * @param attacker 下手的一方
     * @return 世界坐标下的出手点
     */
    static Vec3d referencePointOf(LivingEntity attacker) {
        return attacker.getPos().add(0.0, REFERENCE_HEIGHT_RATIO * attacker.getHeight(), 0.0);
    }

    /**
     * 求出各部位在这一击中的相对权重。
     *
     * <p>攻击者够不着的部位不出现在结果里；权重之和不必为 1，调用方按总和归一即可。返回顺序跟随
     * {@link BodyPartResolver#PART_BOXES}，同一场景下的结果因此稳定可复现。</p>
     *
     * @param player         被攻击的玩家
     * @param referencePoint 攻击者的出手点
     * @return 够得着的部位及其权重，至少一项
     */
    static List<PartWeight> weightsFor(PlayerEntity player, Vec3d referencePoint) {
        Vec3d local = BodyPartResolver.toBodyLocal(player, referencePoint);

        List<PartWeight> reachable = new ArrayList<>();
        for (BodyPartResolver.PartBox candidate : BodyPartResolver.PART_BOXES) {
            if (isReachable(candidate.part(), local)) {
                reachable.add(new PartWeight(candidate.part(), weightOf(candidate.box(), local)));
            }
        }

        // 站位异常等情况下可能一个部位都够不着，此时放回全部部位，避免算不出部位。
        if (reachable.isEmpty()) {
            for (BodyPartResolver.PartBox candidate : BodyPartResolver.PART_BOXES) {
                reachable.add(new PartWeight(candidate.part(), weightOf(candidate.box(), local)));
            }
        }

        return reachable;
    }

    /**
     * 这个部位从攻击者所在的那一侧够不够得着。
     *
     * @param part  部位
     * @param local 出手点在玩家坐标系里的位置
     * @return 够得着返回 {@code true}；没有登记朝向规矩的部位一律为 {@code true}
     */
    private static boolean isReachable(BodyPart part, Vec3d local) {
        Facing facing = FACINGS.get(part);
        return facing == null || facing.reachable(local.x, local.z);
    }

    /**
     * 一个部位的权重：越近越重，按距离平方反比。
     *
     * @param box   部位盒子
     * @param local 出手点在玩家坐标系里的位置
     * @return 权重，恒为正
     */
    private static double weightOf(Box box, Vec3d local) {
        double distance = box.getCenter().distanceTo(local);
        return 1.0 / (distance * distance + DISTANCE_EPSILON);
    }

    /**
     * 一条「从哪一边才够得着」的规矩。
     *
     * @param sideFacing  部位朝向横向的哪一边，负为玩家左手边、正为右手边、0 为不看横向
     * @param depthFacing 部位朝向哪一面，正为身体正面、负为背面、0 为不看前后
     * @param tolerance   允许出手点越过身体中线的距离
     */
    private record Facing(double sideFacing, double depthFacing, double tolerance) {

        /**
         * @param side  出手点的横向坐标，正为玩家右手边
         * @param depth 出手点的前后坐标，正为玩家面朝方向
         * @return 出手点是否落在本部位够得着的那一侧
         */
        boolean reachable(double side, double depth) {
            return this.sideFacing * side + this.depthFacing * depth >= -this.tolerance;
        }
    }

    /**
     * 一个部位与它在这一击里的相对权重。
     *
     * @param part   部位
     * @param weight 相对权重，与其他部位比大小即可，不必凑成 1
     */
    record PartWeight(BodyPart part, double weight) {
    }
}
