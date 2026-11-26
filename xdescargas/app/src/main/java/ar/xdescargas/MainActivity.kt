package ar.xdescargas

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ar.xdescargas.databinding.ActivityMainBinding
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var urlActual: String? = null
    private var videoGuardado: Uri? = null
    private var trabajando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnBuscar.setOnClickListener { buscar(b.inputUrl.text.toString()) }
        b.btnDescargar.setOnClickListener { urlActual?.let { descargar(it) } }
        b.btnAbrir.setOnClickListener { abrirVideo() }
        b.btnCompartir.setOnClickListener { compartirVideo() }

        manejarIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        manejarIntent(intent)
    }

    /** Cuando compartís un tweet desde X, llega acá. */
    private fun manejarIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val texto = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            b.inputUrl.setText(texto)
            buscar(texto)
        }
    }

    /** Saca el link de un tweet de cualquier texto compartido. */
    private fun extraerLink(texto: String): String? {
        val regex = Regex("""https?://(?:www\.|mobile\.)?(?:x|twitter|fxtwitter|vxtwitter|fixupx)\.com/\S+/status/\d+""")
        val link = regex.find(texto)?.value ?: return null
        // Normalizamos a x.com y sacamos cosas tipo ?s=20
        return link.replace(Regex("""//(?:www\.|mobile\.)?(?:twitter|fxtwitter|vxtwitter|fixupx)\.com"""), "//x.com")
    }

    private fun buscar(texto: String) {
        if (trabajando) return
        val url = extraerLink(texto)
        if (url == null) {
            estado("Eso no parece un link de un tweet.")
            return
        }
        urlActual = url
        videoGuardado = null
        mostrarResultado(false)
        b.btnDescargar.visibility = View.GONE
        b.txtTitulo.text = ""

        lifecycleScope.launch {
            ponerTrabajando(true, "Buscando el video…")
            try {
                esperarYtDlp()
                val info = withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().getInfo(YoutubeDLRequest(url))
                }
                val titulo = info.title ?: "Video de X"
                val dur = info.duration
                b.txtTitulo.text = if (dur > 0) "$titulo  ·  ${formatoDuracion(dur)}" else titulo
                b.btnDescargar.visibility = View.VISIBLE
                estado("Listo. ¿Lo descargamos?")
            } catch (e: Exception) {
                estado(mensajeError(e))
            } finally {
                ponerTrabajando(false)
            }
        }
    }

    private fun descargar(url: String) {
        if (trabajando) return
        lifecycleScope.launch {
            ponerTrabajando(true, "Descargando… 0%")
            b.progreso.isIndeterminate = false
            val carpeta = File(cacheDir, "descargas").apply { deleteRecursively(); mkdirs() }
            try {
                esperarYtDlp()
                withContext(Dispatchers.IO) {
                    val req = YoutubeDLRequest(url).apply {
                        addOption("-o", "${carpeta.absolutePath}/%(uploader_id)s_%(id)s.%(ext)s")
                        // Mejor calidad en MP4 (lo que mejor anda en la galería)
                        addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b")
                        addOption("--merge-output-format", "mp4")
                        addOption("--no-playlist")
                        addOption("--no-mtime")
                    }
                    YoutubeDL.getInstance().execute(req, "descarga") { pct, _, _ ->
                        runOnUiThread {
                            val p = pct.toInt().coerceIn(0, 100)
                            b.progreso.progress = p
                            b.txtEstado.text = "Descargando… $p%"
                        }
                    }
                }
                val archivo = carpeta.listFiles()?.maxByOrNull { it.length() }
                    ?: throw IllegalStateException("No se encontró el archivo descargado")
                estado("Guardando en la galería…")
                videoGuardado = withContext(Dispatchers.IO) { guardarEnGaleria(archivo) }
                estado("¡Guardado en Películas/XDescargas!")
                mostrarResultado(true)
            } catch (e: Exception) {
                estado(mensajeError(e))
            } finally {
                carpeta.deleteRecursively()
                b.progreso.isIndeterminate = true
                ponerTrabajando(false)
            }
        }
    }

    /** Copia el video a Películas/XDescargas para que aparezca en la galería. */
    private fun guardarEnGaleria(archivo: File): Uri {
        val valores = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, archivo.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/XDescargas")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, valores)
            ?: throw IllegalStateException("No se pudo crear el archivo en la galería")
        contentResolver.openOutputStream(uri)!!.use { out ->
            archivo.inputStream().use { it.copyTo(out) }
        }
        valores.clear()
        valores.put(MediaStore.Video.Media.IS_PENDING, 0)
        contentResolver.update(uri, valores, null, null)
        return uri
    }

    private fun abrirVideo() {
        val uri = videoGuardado ?: return
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    private fun compartirVideo() {
        val uri = videoGuardado ?: return
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, "Compartir video"))
    }

    private suspend fun esperarYtDlp() {
        (application as App).listo.await().getOrElse {
            throw IllegalStateException("No se pudo iniciar yt-dlp: ${it.message}")
        }
    }

    private fun mensajeError(e: Exception): String {
        val m = e.message.orEmpty()
        return when {
            "No video could be found" in m || "no video" in m.lowercase() ->
                "Ese tweet no tiene video (o es una foto/GIF raro)."
            "login" in m.lowercase() || "NSFW" in m || "age" in m.lowercase() ->
                "Ese tweet pide estar logueado (privado o +18). Este no se puede bajar."
            "Unable to download" in m || "HTTP Error" in m ->
                "X no respondió. Probá de nuevo en un rato."
            else -> "Error: ${m.lines().lastOrNull { it.isNotBlank() } ?: e.javaClass.simpleName}"
        }
    }

    private fun ponerTrabajando(si: Boolean, texto: String? = null) {
        trabajando = si
        b.progreso.visibility = if (si) View.VISIBLE else View.GONE
        b.btnBuscar.isEnabled = !si
        b.btnDescargar.isEnabled = !si
        texto?.let { estado(it) }
    }

    private fun mostrarResultado(si: Boolean) {
        val v = if (si) View.VISIBLE else View.GONE
        b.btnAbrir.visibility = v
        b.btnCompartir.visibility = v
    }

    private fun estado(t: String) { b.txtEstado.text = t }

    private fun formatoDuracion(seg: Int) = "%d:%02d".format(seg / 60, seg % 60)

    override fun onDestroy() {
        if (isFinishing && trabajando) {
            runCatching { YoutubeDL.getInstance().destroyProcessById("descarga") }
        }
        super.onDestroy()
    }

    @Suppress("unused")
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
