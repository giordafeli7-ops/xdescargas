# XDescargas

App de Android para bajar videos de X/Twitter. Todo corre en el celu: no hay servidor ni web.

## Cómo se usa
1. En X, abrí un tweet con video → **Compartir** → **XDescargas**.
2. La app te muestra el título y la duración → **Descargar**.
3. El video queda en la galería, en `Películas/XDescargas`. Ahí mismo podés verlo o compartirlo.

También podés abrir la app y pegar el link a mano.

## Cómo sacar el APK

### Opción A: GitHub, sin instalar nada (se puede hacer desde el celu)
1. Creá un repo **privado** en github.com y subí todos estos archivos (con la carpeta `.github`).
2. Andá a la pestaña **Actions**: se compila solo en ~5 min.
3. Entrá al build terminado → **Artifacts** → bajá `XDescargas-apk` (viene en un .zip con el APK adentro).
4. Abrí el APK en el celu y aceptá "instalar apps de origen desconocido".

### Opción B: Android Studio en la compu
1. File → Open → esta carpeta. Esperá que termine de sincronizar.
2. Conectá el celu con la depuración USB activada y dale a ▶ Run.

## Notas
- Pide Android 10 o más nuevo.
- Pesa unos ~70 MB porque trae Python, yt-dlp y ffmpeg adentro.
- Cada vez que se abre, actualiza yt-dlp en segundo plano, así que si X cambia algo, suele arreglarse solo.
- Los tweets privados o con restricción +18 no se pueden bajar (piden login).
