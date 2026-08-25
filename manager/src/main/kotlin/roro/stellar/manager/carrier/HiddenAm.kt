package roro.stellar.manager.carrier

import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import org.lsposed.hiddenapibypass.HiddenApiBypass
import roro.stellar.Stellar
import roro.stellar.StellarBinderWrapper
import roro.stellar.manager.util.UserHandleCompat

internal object HiddenAm {
    fun activityManager(): Any {
        val sm = Class.forName("android.os.ServiceManager")
        val raw = sm.getMethod("getService", String::class.java)
            .invoke(null, "activity") as IBinder
        val binder = if (Stellar.pingBinder()) StellarBinderWrapper(raw) else raw
        val stub = Class.forName("android.app.IActivityManager\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
    }

    /**
     * Reads instrumentation flags from the running framework.
     * Returns null when `INSTR_FLAG_NO_RESTART` is absent: AMS then
     * `forceStopPackage`s the target, which kills the manager UI.
     */
    fun flagsForNoRestartInstrumentation(): Int? {
        val am = Class.forName("android.app.ActivityManager")
        val noRestart = intConstant(am, "INSTR_FLAG_NO_RESTART") ?: return null
        val disableHidden = intConstant(am, "INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS") ?: 0
        return noRestart or disableHidden
    }

    fun startDelegate(am: Any, uid: Int) {
        invoke(
            am.javaClass.methods.first {
                it.name == "startDelegateShellPermissionIdentity" && it.parameterCount == 2
            },
            am,
            uid,
            null
        )
    }

    fun stopDelegate(am: Any) {
        invoke(
            am.javaClass.methods.first {
                it.name == "stopDelegateShellPermissionIdentity" && it.parameterCount == 0
            },
            am
        )
    }

    fun startInstrumentation(
        am: Any,
        name: ComponentName,
        flags: Int,
        arguments: Bundle,
        connection: Any = uiAutomationConnection()
    ): Boolean {
        val method = am.javaClass.methods.first {
            it.name == "startInstrumentation" && it.parameterCount == 8
        }
        val result = invoke(
            method,
            am,
            name,
            null,
            flags,
            arguments,
            null,
            connection,
            UserHandleCompat.myUserId(),
            null
        )
        return result as? Boolean ?: true
    }

    fun uiAutomationConnection(): Any {
        val ctor = Class.forName("android.app.UiAutomationConnection").getDeclaredConstructor()
        ctor.isAccessible = true
        return ctor.newInstance()
    }

    private fun invoke(method: java.lang.reflect.Method, target: Any, vararg args: Any?): Any? {
        return try {
            method.invoke(target, *args)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun intConstant(clazz: Class<*>, name: String): Int? {
        val field = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.getStaticFields(clazz).firstOrNull { it.name == name }
        } else {
            runCatching { clazz.getDeclaredField(name) }.getOrNull()
        } ?: return null
        field.isAccessible = true
        return runCatching { field.getInt(null) }.getOrNull()
    }
}
