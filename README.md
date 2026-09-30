# Lector personal EPUB

Aplicación Android nativa para leer EPUB, guardar palabras y frases en un diccionario personal, y anotar fragmentos mediante Locators estables de Readium.

## Funciones

- Biblioteca local ordenada por última lectura e importación desde el selector de archivos de Android.
- Controles en barras separadas del viewport (sin tapar texto), páginas del recurso EPUB actual, progreso total, índice navegable, anterior/siguiente y superficies/sistema que siguen el tema Readium activo.
- Marcadores por libro, gestión desde el índice y restauración de la última posición exacta.
- Tamaño de texto y tema claro, sepia u oscuro, guardados por libro.
- Diccionario personal con traducciones opcionales y colores; los resaltados parciales se limitan a la página actual y tres páginas a cada lado dentro del recurso EPUB activo, y se cachean por EPUB.
- Notas libres ancladas a una selección y navegación de regreso al fragmento.
- Acción opcional `ACTION_PROCESS_TEXT` para enviar texto seleccionado a Offline Translator (`dev.davidv.translator`).

Los libros importados y los datos personales se guardan localmente en la aplicación. No se sincronizan ni se envían a un servidor. La traducción requiere instalar por separado Offline Translator.

## Requisitos

- Android Studio con JDK 17.
- Android SDK Platform 36 para compilar.
- Android 8.0 (API 26) o posterior para ejecutar.

## Compilar y probar

Desde esta carpeta, ejecuta:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

El APK debug se genera en `app/build/outputs/apk/debug/app-debug.apk`. Abre esta carpeta como proyecto Gradle en Android Studio, instala la app, importa un archivo EPUB y ábrelo desde la biblioteca.

## Notas de validación

- La apertura EPUB, el diccionario y las notas se probaron con publicaciones reales en el emulador.
- La compilación y los tests unitarios pasan con Gradle 9.3.0.
- Offline Translator 0.8.5 resolvió la acción en un AVD x86_64, pero su proceso falló por una incompatibilidad de la biblioteca nativa JNA del APK arm64. La acción debe verificarse en un teléfono ARM64.
- Los controles de lectura y el redibujado al paginar se probaron en AVD Pixel_10 con un EPUB sintético; falta una pasada equivalente en teléfono físico. Offline Translator aún requiere validación ARM64.

## Licencia

MIT. Consulta `LICENSE`.
