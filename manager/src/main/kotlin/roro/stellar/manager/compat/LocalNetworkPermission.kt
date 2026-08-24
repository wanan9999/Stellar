package roro.stellar.manager.compat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

object LocalNetwork {
    const val PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK

    fun isRequired(): Boolean = BuildUtils.atLeast37

    fun hasAccess(context: Context): Boolean {
        if (!isRequired()) return true
        return ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun LocalNetworkPermissionRequester(onResult: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val required = LocalNetwork.isRequired()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }
    LaunchedEffect(required) {
        if (!required) return@LaunchedEffect
        if (LocalNetwork.hasAccess(context)) {
            onResult(true)
        } else {
            launcher.launch(LocalNetwork.PERMISSION)
        }
    }
}
