package org.twcore.bodypart;

import java.util.List;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import org.twcore.api.bodypart.BodyPart;

/**
 * 把弹射物在玩家身上的命中点换算成身体部位。
 *
 * <p>命中点是世界坐标，而「打在哪个部位」是相对玩家身体的说法，因此先把命中点换算到玩家自身的
 * 坐标系：原点在脚底正中，纵轴向上，横轴分别指向玩家右手边与面朝方向。换算结果依次与各部位盒子
 * 比较，落不进任何盒子时归给最近的一个。</p>
 *
 * <p>部位盒子按玩家模型划定，横向比碰撞箱更宽（手臂位于碰撞箱之外），因此不能改用碰撞箱划分。</p>
 */
public final class BodyPartResolver {

    /**
     * 头：玩家模型最上面那一块。
     */
    private static final Box HEAD_BOX = new Box(-0.2, 1.5, -0.2, 0.2, 1.8, 0.2);

    /**
     * 右肩：躯干右侧向外伸出的那一条，即玩家自身的右手边。
     */
    private static final Box RIGHT_SHOULDER_BOX = new Box(0.3, 0.7, -0.15, 0.4, 1.5, 0.15);

    /**
     * 左肩：躯干左侧向外伸出的那一条。
     */
    private static final Box LEFT_SHOULDER_BOX = new Box(-0.4, 0.7, -0.15, -0.3, 1.5, 0.15);

    /**
     * 正胸：躯干前半的上半。
     */
    private static final Box CHEST_BOX = new Box(-0.3, 1.1, 0.0, 0.3, 1.5, 0.15);

    /**
     * 腹部：躯干前半的下半。
     */
    private static final Box ABDOMEN_BOX = new Box(-0.3, 0.7, 0.0, 0.3, 1.1, 0.15);

    /**
     * 后背：躯干后半整块，从腰到肩胛。
     */
    private static final Box BACK_BOX = new Box(-0.3, 0.7, -0.15, 0.3, 1.5, 0.0);

    /**
     * 腿与脚：合并成一段，不区分左右。
     */
    private static final Box LEGS_BOX = new Box(-0.2, 0.0, -0.2, 0.2, 0.7, 0.2);

    /**
     * 站立时的身高，也是各盒子高度数值的基准。
     */
    private static final double STANDING_HEIGHT = 1.8;

    /**
     * 参与判定的部位盒子，顺序即优先级。
     *
     * <p>窄小的部位排在前面，躯干排在后面：同时贴着肩膀盒与躯干盒的点会算成肩膀，否则那两条又窄
     * 又靠外的盒子永远争不过中间的大块躯干。后背排在最后，使正好落在前后分界线上的点算作正面。</p>
     */
    static final List<PartBox> PART_BOXES = List.of(
            new PartBox(HEAD_BOX, BodyPart.HEAD),
            new PartBox(RIGHT_SHOULDER_BOX, BodyPart.RIGHT_SHOULDER),
            new PartBox(LEFT_SHOULDER_BOX, BodyPart.LEFT_SHOULDER),
            new PartBox(LEGS_BOX, BodyPart.LEGS),
            new PartBox(CHEST_BOX, BodyPart.CHEST),
            new PartBox(ABDOMEN_BOX, BodyPart.ABDOMEN),
            new PartBox(BACK_BOX, BodyPart.BACK));

    private BodyPartResolver() {
    }

    /**
     * 判断一次命中落在玩家的哪个部位。
     *
     * @param player 被命中的玩家，以他当前姿态的位置与朝向为基准
     * @param hitPos 命中点的世界坐标
     * @return 命中的部位
     * @since 1.0.5
     */
    public static BodyPart resolve(PlayerEntity player, Vec3d hitPos) {
        Vec3d local = toBodyLocal(player, hitPos);

        for (PartBox candidate : PART_BOXES) {
            if (candidate.box().contains(local)) {
                return candidate.part();
            }
        }

        // 命中点落在身体表面，而盒子都在身体内部，多数情况下落不进任何盒子，
        // 因此归给最近的一个；以第一项为起点，保证总能有结果。
        PartBox nearest = PART_BOXES.get(0);
        double nearestDistance = distanceTo(local, nearest.box());
        for (int i = 1; i < PART_BOXES.size(); i++) {
            PartBox candidate = PART_BOXES.get(i);
            double distance = distanceTo(local, candidate.box());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest.part();
    }

    /**
     * 把世界坐标的点换算到玩家自身的坐标系。
     *
     * <p>结果的三项依次为横向（正为玩家右手边）、离脚底的高度、前后（正为玩家面朝方向）。站立的
     * 玩家按高度直接量取；躺平的姿态（游泳、爬行、鞘翅滑翔、激流、睡觉）改用前后方向的偏移充当
     * 高度，此时面向的一侧是头、背面的一侧是腿，并要按趴着还是仰着决定前后方向的正负。</p>
     *
     * @param player 作为基准的玩家
     * @param point  世界坐标下的点
     * @return 该点在玩家坐标系里的位置
     */
    static Vec3d toBodyLocal(PlayerEntity player, Vec3d point) {
        Vec3d relative = point.subtract(player.getPos());

        double yaw = Math.toRadians(player.getYaw());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);

        double side = relative.x * -forwardZ + relative.z * forwardX;
        double depth = relative.x * forwardX + relative.z * forwardZ;

        if (isLyingFlat(player)) {
            // 躺平后两根轴对调：世界的前后偏移成为身体高度，世界的上下偏移成为身体前后。
            // 高度照旧平移半身高，以便套用直立时量好的盒子。
            double bellyUp = player.getPose() == EntityPose.SLEEPING ? 1.0 : -1.0;
            return new Vec3d(side, depth + STANDING_HEIGHT / 2.0,
                    bellyUp * (relative.y - player.getHeight() / 2.0));
        }

        // 潜行等姿态按当前身高与站立身高的比例还原高度，使头顶仍落在盒子内。
        double scale = player.getHeight() / STANDING_HEIGHT;
        return new Vec3d(side, relative.y / scale, depth);
    }

    /**
     * 玩家此刻是否躺平。
     *
     * <p>激流冲刺必须在列：它是三叉戟拖着人向前冲，人同样横着，漏掉会让按比例还原高度的分支把
     * 身体拉长三倍。潜行虽然压矮到 1.5 格，人仍然竖着，因此不算躺平。</p>
     */
    private static boolean isLyingFlat(PlayerEntity player) {
        EntityPose pose = player.getPose();
        return pose == EntityPose.SWIMMING
                || pose == EntityPose.FALL_FLYING
                || pose == EntityPose.SPIN_ATTACK
                || pose == EntityPose.SLEEPING;
    }

    /**
     * 求一点到盒子的最短距离；点落在盒内时为 0。
     */
    private static double distanceTo(Vec3d point, Box box) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 一个部位盒子与它代表的部位。
     */
    record PartBox(Box box, BodyPart part) {
    }
}
