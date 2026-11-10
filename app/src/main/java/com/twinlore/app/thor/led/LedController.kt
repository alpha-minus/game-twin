package com.twinlore.app.thor.led

import android.os.IBinder
import android.os.Parcel
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Controls Thor (AYN Thor and similar) LED rings around analog sticks via PServerBinder/sysfs.
 * On non-Thor devices, all operations no-op; no crashes.
 */
class LedController {

    private val pServerBinder: IBinder?
    private val lock = ReentrantLock()

    private var lastCommand: String? = null
    private var lastExecuteTime = 0L
    private val minExecuteInterval = 8L

    init {
        pServerBinder = try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
            getService.invoke(serviceManager, "PServerBinder") as? IBinder
        } catch (e: Exception) {
            null
        }
    }

    fun setLedColor(
        red: Int,
        green: Int,
        blue: Int,
        brightness: Int = 255,
        leftTop: Boolean = true,
        leftBottom: Boolean = true,
        rightTop: Boolean = true,
        rightBottom: Boolean = true
    ) {
        val commands = mutableListOf<String>()
        val r = red.coerceIn(0, 255)
        val g = green.coerceIn(0, 255)
        val b = blue.coerceIn(0, 255)
        val bright = brightness.coerceIn(0, 255)

        if (leftTop) {
            commands.add("echo 1-$r:$g:$b:$bright > /sys/class/sn3112l/led/brightness")
        }
        if (leftBottom) {
            commands.add("echo 2-$r:$g:$b:$bright > /sys/class/sn3112l/led/brightness")
        }
        if (rightTop) {
            commands.add("echo 1-$r:$g:$b:$bright > /sys/class/sn3112r/led/brightness")
        }
        if (rightBottom) {
            commands.add("echo 2-$r:$g:$b:$bright > /sys/class/sn3112r/led/brightness")
        }

        if (commands.isNotEmpty()) {
            val command = commands.joinToString(" && ")
            executeCommandDirect(command)
        }
    }

    private fun executeCommandDirect(command: String) {
        lock.withLock {
            val now = System.currentTimeMillis()
            if (command == lastCommand && now - lastExecuteTime < minExecuteInterval) {
                return
            }
            lastCommand = command
            lastExecuteTime = now
            try {
                pServerBinder?.let { binder ->
                    val data = Parcel.obtain()
                    val reply = Parcel.obtain()
                    try {
                        data.writeStringArray(arrayOf(command, "1"))
                        binder.transact(0, data, reply, IBinder.FLAG_ONEWAY)
                    } finally {
                        data.recycle()
                        reply.recycle()
                    }
                }
            } catch (e: Exception) {
                // No-op on non-Thor or failure; avoid crashes
            }
        }
    }
}
