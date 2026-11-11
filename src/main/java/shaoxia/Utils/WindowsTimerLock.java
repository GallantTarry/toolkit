package shaoxia.Utils;

import com.sun.jna.Library;
import com.sun.jna.Native;

/**
 * Windows 1ms 硬件时钟精度锁 (Hardware Timer Resolution Lock)
 * <p>
 * 解决 Windows 操作系统默认系统中断时钟粒度漂移问题（15.625ms 调度中断周期导致 60 FPS 定时器在 16ms~31ms 来回跳变与掉帧抖动）。
 * 1. 通过 Win32 Multimedia API (winmm.dll) 调用 timeBeginPeriod(1) 将系统中断精度锁死为 1.0ms；
 * 2. 辅以底层轻量守护线程锚点（Timer Anchor Daemon），防止现代 Windows 10/11 的电源节电与空闲调度自动回退；
 * 3. 注册 JVM 关闭钩子（ShutdownHook），在程序退出时优雅调用 timeEndPeriod(1) 恢复系统原始时钟粒度。
 */
public final class WindowsTimerLock {

    private interface WinMM extends Library {
        WinMM INSTANCE = Native.load("winmm", WinMM.class);

        int timeBeginPeriod(int uPeriod);

        int timeEndPeriod(int uPeriod);
    }

    private static volatile boolean initialized = false;
    private static volatile boolean winMmLocked = false;
    private static Thread anchorThread = null;

    private WindowsTimerLock() {
        // 工具类禁止实例化
    }

    /**
     * 激活 Windows 1ms 硬件时钟精度锁
     */
    public static synchronized void lock1ms() {
        if (initialized) {
            return;
        }
        initialized = true;

        String osName = System.getProperty("os.name", "").toLowerCase();
        if (!osName.contains("win")) {
            return;
        }

        // 1. 尝试通过 WinMM 底层调用 timeBeginPeriod(1)
        try {
            int ret = WinMM.INSTANCE.timeBeginPeriod(1);
            if (ret == 0) { // TIMERR_NOERROR = 0
                winMmLocked = true;
                System.out.println("[WindowsTimerLock] 成功激活 Windows 1ms 硬件时钟精度锁 (winmm.timeBeginPeriod)");
            } else {
                System.err.println("[WindowsTimerLock] timeBeginPeriod(1) 返回非零错误码: " + ret);
            }
        } catch (Throwable t) {
            System.err.println("[WindowsTimerLock] JNA 加载 winmm.dll 失败，将采用纯 Java 锚点回退机制: " + t.getMessage());
        }

        // 2. 启动防休眠/防节电回退的守护锚点线程（Minecraft/LWJGL 黄金标准）
        try {
            anchorThread = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(Long.MAX_VALUE);
                    }
                } catch (InterruptedException ignored) {
                    // 正常退出
                }
            }, "WindowsTimerResolutionAnchor");
            anchorThread.setDaemon(true);
            anchorThread.setPriority(Thread.MIN_PRIORITY);
            anchorThread.start();
        } catch (Throwable t) {
            System.err.println("[WindowsTimerLock] 启动时钟锚点守护线程失败: " + t.getMessage());
        }

        // 3. 注册 JVM 关闭钩子，确保释放时钟锁
        Runtime.getRuntime().addShutdownHook(new Thread(WindowsTimerLock::release, "WindowsTimerLockShutdownHook"));
    }

    /**
     * 释放时钟精度锁（通常由 ShutdownHook 自动触发）
     */
    public static synchronized void release() {
        if (winMmLocked) {
            try {
                int ret = WinMM.INSTANCE.timeEndPeriod(1);
                if (ret == 0) {
                    System.out.println("[WindowsTimerLock] 成功释放 Windows 1ms 硬件时钟精度锁 (winmm.timeEndPeriod)");
                }
            } catch (Throwable ignored) {
            } finally {
                winMmLocked = false;
            }
        }
        if (anchorThread != null && anchorThread.isAlive()) {
            try {
                anchorThread.interrupt();
            } catch (Throwable ignored) {
            }
            anchorThread = null;
        }
    }
}
