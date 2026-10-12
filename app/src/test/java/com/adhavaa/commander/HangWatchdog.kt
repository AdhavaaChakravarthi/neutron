package com.adhavaa.commander

import java.io.File

/**
 * If the unit tests run longer than [LIMIT_MS], writes every thread's stack to build/hang-dump.txt and stops the
 * test JVM, so CI fails with a readable cause instead of hanging. Started by the Robolectric test classes.
 */
object HangWatchdog {
    private const val LIMIT_MS = 9 * 60 * 1000L
    @Volatile private var started = false
    @Volatile var current: String = "?"

    @Synchronized
    fun start(test: String) {
        current = test
        if (started) return
        started = true
        val t = Thread {
            Thread.sleep(LIMIT_MS)
            val out = StringBuilder("HANG while running: $current\n")
            for ((thread, stack) in Thread.getAllStackTraces()) {
                out.append("\n\"${thread.name}\" ${thread.state}\n")
                stack.take(40).forEach { out.append("    at $it\n") }
            }
            val dir = File(System.getProperty("screenshot.dir") ?: "build/screenshots").parentFile ?: File("build")
            File(dir, "hang-dump.txt").writeText(out.toString())
            System.err.println(out)
            Runtime.getRuntime().halt(3)
        }
        t.isDaemon = true
        t.name = "hang-watchdog"
        t.start()
    }
}
