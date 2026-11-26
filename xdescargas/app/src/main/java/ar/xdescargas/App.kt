package ar.xdescargas

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    /** Se completa cuando yt-dlp está listo para usar. */
    val listo = CompletableDeferred<Result<Unit>>()

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val r = runCatching {
                YoutubeDL.getInstance().init(this@App)
                FFmpeg.getInstance().init(this@App)
            }
            listo.complete(r)
            // X cambia seguido: intentamos actualizar yt-dlp en segundo plano.
            if (r.isSuccess) runCatching {
                YoutubeDL.getInstance().updateYoutubeDL(this@App, YoutubeDL.UpdateChannel.STABLE)
            }.onFailure { Log.w("XDescargas", "No se pudo actualizar yt-dlp", it) }
        }
    }
}
