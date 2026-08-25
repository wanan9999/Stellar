package roro.stellar.manager.carrier

import android.os.IBinder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import roro.stellar.Stellar
import roro.stellar.manager.BuildConfig
import roro.stellar.userservice.ServiceMode
import roro.stellar.userservice.StellarUserService
import roro.stellar.userservice.UserServiceArgs
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object CarrierClient {
    private val mutex = Mutex()
    @Volatile private var bound: ICarrierOverrideService? = null

    private fun args() = UserServiceArgs.Builder(CarrierUserService::class.java)
        .processNameSuffix("carrier")
        .versionCode(BuildConfig.VERSION_CODE.toLong())
        .serviceMode(ServiceMode.DAEMON)
        .debug(BuildConfig.DEBUG)
        .build()

    suspend fun ensure(): ICarrierOverrideService = mutex.withLock {
        val current = bound
        if (current?.asBinder()?.pingBinder() == true) return current
        if (!Stellar.pingBinder()) error("Stellar 服务未运行")
        bindLocked()
    }

    fun unbind() {
        runCatching { StellarUserService.unbindUserService(args()) }
        bound = null
    }

    private suspend fun bindLocked(): ICarrierOverrideService {
        return suspendCancellableCoroutine { cont ->
            StellarUserService.bindUserService(args(), object : StellarUserService.ServiceCallback {
                override fun onServiceConnected(service: IBinder) {
                    val remote = ICarrierOverrideService.Stub.asInterface(service)
                    bound = remote
                    if (cont.isActive) cont.resume(remote)
                }

                override fun onServiceDisconnected() {
                    bound = null
                }

                override fun onServiceStartFailed(errorCode: Int, message: String) {
                    bound = null
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("[$errorCode] $message"))
                }
            })
        }
    }
}
