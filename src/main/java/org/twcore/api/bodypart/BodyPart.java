package org.twcore.api.bodypart;

/**
 * <h1>玩家身体部位</h1>
 * <p>
 * 玩家身体按判定需要划分为七个部位，用于回答一次命中落在身体的哪一处。各部位的位置与范围由系统
 * 内部按玩家模型定义，本枚举只给出这些部位的概念名称。
 * </p>
 * <p>
 * 躯干区分正反两面：{@link #CHEST} 与 {@link #ABDOMEN} 占躯干前半，{@link #BACK} 占后半。因此从
 * 背后袭来的命中落在 {@code BACK} 上，正面命中落在 {@code CHEST} 或 {@code ABDOMEN} 上。
 * </p>
 * <p>
 * 弹射物的部位由命中点换算而来，近战的部位按攻击者与受击者的站位推定，两者都只在服务端产生。
 * </p>
 *
 * @see BodyPartHit
 * @see org.twcore.api.event.BodyPartHitEvent
 * @since 1.0.5
 */
public enum BodyPart {

    /**
     * 腿部：身体最下面的一段。
     */
    LEGS,

    /**
     * 腹部：躯干下半的前面。
     */
    ABDOMEN,

    /**
     * 后背：躯干后面整块，从腰到肩胛。
     */
    BACK,

    /**
     * 正胸：躯干上半的前面。
     */
    CHEST,

    /**
     * 左肩：上半身偏玩家左手边的一侧。
     */
    LEFT_SHOULDER,

    /**
     * 右肩：上半身偏玩家右手边的一侧。
     */
    RIGHT_SHOULDER,

    /**
     * 头部：身体最上面的一段。
     */
    HEAD
}
