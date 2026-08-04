package cn.net.rjnetwork.xianyu.chrome.human;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * 人类行为延时工具：把自动化操作的固定间隔 / 瞬时动作改为带随机性的「拟人」行为，
 * 降低被行为风控判定为机器的概率。
 *
 * <p>能力：
 * <ul>
 *   <li>{@link #sleep(long, long)} — 随机区间休眠（替代固定 {@code Thread.sleep}）</li>
 *   <li>{@link #random(long, long)} — 区间随机数</li>
 *   <li>{@link #mouseTrajectory(int, int, int, int)} — 生成带缓动与噪声的鼠标移动轨迹点
 *       （替代单次 mouseMove 跳变）</li>
 *   <li>{@link #clickOffset()} — 点击中心随机偏移（替代永远点正中心）</li>
 * </ul>
 */
public final class HumanDelay {

    private static final SecureRandom RANDOM = new SecureRandom();

    private HumanDelay() {}

    /** 区间随机数 [min, max]。 */
    public static long random(long min, long max) {
        if (max <= min) {
            return min;
        }
        return min + (long) (RANDOM.nextDouble() * (max - min + 1));
    }

    /** 区间随机休眠（毫秒），中断时恢复中断标志并抛出。 */
    public static void sleep(long minMs, long maxMs) throws InterruptedException {
        long ms = random(minMs, maxMs);
        Thread.sleep(ms);
    }

    /** 随机休眠，忽略中断（供无需感知中断的调用点使用）。 */
    public static void sleepQuietly(long minMs, long maxMs) {
        try {
            sleep(minMs, maxMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** 点击中心随机偏移（±maxPx，默认 ±4px，真人不会精确点正中心）。 */
    public static int clickOffset() {
        return clickOffset(4);
    }

    /** 点击中心随机偏移（±maxPx）。 */
    public static int clickOffset(int maxPx) {
        if (maxPx <= 0) {
            return 0;
        }
        return RANDOM.nextInt(2 * maxPx + 1) - maxPx;
    }

    /** 鼠标移动轨迹点（含起点终点）。 */
    public record TrajectoryPoint(int x, int y) {
    }

    /**
     * 生成从 (x1,y1) 到 (x2,y2) 的鼠标移动轨迹点。
     *
     * <p>使用 ease-out 缓动（先快后慢）+ 高斯噪声扰动，步骤数随机 6~12 步，
     * 模拟真人鼠标从当前位置滑向目标的运动曲线（替代单次 mouseMove 跳变）。
     */
    public static List<TrajectoryPoint> mouseTrajectory(int x1, int y1, int x2, int y2) {
        int steps = 6 + RANDOM.nextInt(7); // 6 ~ 12
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dist = Math.hypot(dx, dy);
        // 噪声幅度随距离增大（长距离允许更明显的抖动）
        double noiseScale = Math.max(0.5, dist / 400.0);

        List<TrajectoryPoint> points = new ArrayList<>(steps + 1);
        points.add(new TrajectoryPoint(x1, y1));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            // ease-out 缓动：t^1.6（先快后慢）
            double e = Math.pow(t, 1.6);
            int px = (int) Math.round(x1 + dx * e);
            int py = (int) Math.round(y1 + dy * e);
            // 高斯噪声（Box-Muller 简化：两随机数平均近似）
            double noise = ((RANDOM.nextDouble() + RANDOM.nextDouble() + RANDOM.nextDouble()) / 1.5 - 1.0) * noiseScale * 3.0;
            px += (int) Math.round(noise);
            py += (int) Math.round(noise);
            points.add(new TrajectoryPoint(px, py));
        }
        // 终点精确命中目标
        points.set(points.size() - 1, new TrajectoryPoint(x2, y2));
        return points;
    }
}
