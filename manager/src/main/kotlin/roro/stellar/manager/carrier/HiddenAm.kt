package roro.stellar.manager.carrier

import android.content.ComponentName
import android.os.Bundle
import android.os.IBinder

internal object HiddenAm {
    const val INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS = 0x00000002
    const val INSTR_FLAG_NO_RESTART = 0x00000010

    fun activityManager(): Any {
        val sm = Class.forName("android.os.ServiceManager")
        val binder = sm.getMethod("getService", String::class.java)
            .invoke(null, "activity") as IBinder
        val stub = Class.forName("android.app.IActivityManager\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
    }

    fun startDelegate(am: Any, uid: Int) {
        am.javaClass.methods.first {
            it.name == "startDelegateShellPermissionIdentity" && it.parameterCount == 2
        }.invoke(am, uid, null)
    }

    fun stopDelegate(am: Any) {
        am.javaClass.methods.first {
            it.name == "stopDelegateShellPermissionIdentity" && it.parameterCount == 0
        }.invoke(am)
    }

    fun startInstrumentation(
        am: Any,
        name: ComponentName,
        flags: Int,
        arguments: Bundle
    ): Boolean {
        val connection = Class.forName("android.app.UiAutomationConnection")
            .getDeclaredConstructor()
            .apply { isAccessible = true }
            .newInstance()
        val method = am.javaClass.methods.first {
            it.name == "startInstrumentation" && it.parameterCount == 8
        }
        val result = method.invoke(
            am,
            name,
            null,
            flags,
            arguments,
            null,
            connection,
            0,
            null
        )
        return result as? Boolean ?: true
    }
}
