package shaoxia.Utils;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 全局统一 60 FPS 动画时钟驱动器 (Centralized 60 FPS Frame Pacer)
 * <p>
 * 彻底消除多组件自建低精度 Timer 导致的 TimerQueue 锁争用、相位错位、EDT 频繁唤醒及重绘无法合并等瓶颈。
 * 1. 唯一主时钟脉冲：全应用共享单个 16.6ms (60 FPS) 心跳；
 * 2. 智能按需启停：活跃动画数为 0 时自动休眠，实现 0 CPU/GPU 额外损耗；
 * 3. 帧合并与脏区协同：在同一次时钟周期内集中驱动所有活跃组件插值，使 AWT RepaintManager 能够高效合并重绘脏区；
 * 4. 异常隔离：单个组件动画异常不影响全局时钟与其他组件运行。
 */
public final class FrameTicker {

    /**
     * 帧动画回调接口
     */
    @FunctionalInterface
    public interface FrameListener {
        /**
         * 帧心跳脉冲触发
         *
         * @param deltaSeconds 距上一帧的实际物理时间增量（秒，通常约为 0.0166s）
         * @return true 继续保持监听；false 表示本次动画已播放完毕，自动注销
         */
        boolean onFrame(double deltaSeconds);
    }

    private static final int TARGET_FPS = 60;
    private static final int FRAME_INTERVAL_MS = 16; // 1000ms / 60 ≈ 16.6ms

    private static final List<FrameListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Timer MASTER_TIMER;
    private static long lastTickNano = 0;

    static {
        MASTER_TIMER = new Timer(FRAME_INTERVAL_MS, e -> onMasterTick());
        MASTER_TIMER.setRepeats(true);
        MASTER_TIMER.setCoalesce(true); // 保证堆积事件自动合并
    }

    private FrameTicker() {
        // 工具类禁止实例化
    }

    /**
     * 注册一个帧动画监听器
     *
     * @param listener 帧回调
     */
    public static void register(FrameListener listener) {
        if (listener == null) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> register(listener));
            return;
        }

        if (!LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }

        if (!MASTER_TIMER.isRunning() && !LISTENERS.isEmpty()) {
            lastTickNano = System.nanoTime();
            MASTER_TIMER.start();
        }
    }

    /**
     * 注销帧动画监听器
     *
     * @param listener 帧回调
     */
    public static void unregister(FrameListener listener) {
        if (listener == null) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> unregister(listener));
            return;
        }

        LISTENERS.remove(listener);

        if (LISTENERS.isEmpty() && MASTER_TIMER.isRunning()) {
            MASTER_TIMER.stop();
        }
    }

    /**
     * 判断指定监听器当前是否正在被时钟驱动
     */
    public static boolean isRegistered(FrameListener listener) {
        return listener != null && LISTENERS.contains(listener);
    }

    /**
     * 当前是否有活跃的 60 帧动画正在运行
     */
    public static boolean isRunning() {
        return MASTER_TIMER.isRunning();
    }

    /**
     * 获取当前活跃监听器数量
     */
    public static int getActiveListenerCount() {
        return LISTENERS.size();
    }

    /**
     * 主时钟滴答（严格在 EDT 上执行）
     */
    private static void onMasterTick() {
        if (LISTENERS.isEmpty()) {
            MASTER_TIMER.stop();
            return;
        }

        long now = System.nanoTime();
        if (lastTickNano <= 0) {
            lastTickNano = now;
        }
        double delta = (now - lastTickNano) / 1_000_000_000.0;
        lastTickNano = now;

        // 防止窗口最小化或调试挂起后的巨大时间跳跃
        delta = Math.min(delta, 0.05);

        for (FrameListener listener : LISTENERS) {
            try {
                boolean keepAlive = listener.onFrame(delta);
                if (!keepAlive) {
                    LISTENERS.remove(listener);
                }
            } catch (Throwable t) {
                System.err.println("[FrameTicker] 动画帧回调执行异常: " + t.getMessage());
                t.printStackTrace();
                LISTENERS.remove(listener);
            }
        }

        if (LISTENERS.isEmpty() && MASTER_TIMER.isRunning()) {
            MASTER_TIMER.stop();
        }
    }
}
