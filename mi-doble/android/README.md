# Mi Doble — Android

## Beta de selección de modelos

La pantalla presenta tres modelos por nombre: Phi-4, Qwen3 y Gemma 3. El usuario puede cambiar la selección en cualquier momento. Los modelos descargados se guardan en el almacenamiento privado de la aplicación y no se eliminan al actualizar la app.

En esta compilación, únicamente Qwen3 tiene un archivo GGUF con tamaño y SHA-256 validados en el descargador. Los otros dos aparecen en el catálogo y se pueden seleccionar, pero su instalación permanece bloqueada hasta que se fijen y verifiquen artefactos oficiales compatibles. No se debe mostrar su estado como instalado ni cambiar el modelo activo si no se ha cargado correctamente.

## Voz

El usuario puede configurar la voz del dispositivo, indicar que desea su voz personalizada o elegir otra voz, y activar su uso en llamadas/reuniones. Esta versión guarda esas preferencias localmente. La clonación de voz y el uso real en llamadas o reuniones requieren un motor de voz compatible y conectores de comunicación; no deben anunciarse como activos hasta instalarlos y probarlos. Al hablar con terceros, Mi Doble debe identificarse como asistente de IA.

## Runtime local

- Android 14 o posterior (minSdk 34).
- llama.cpp nativo a través de JNI, CPU-first.
- Modelo Qwen3 conservado en almacenamiento privado.
- El modelo se descarga únicamente cuando el usuario pulsa instalar.
- Se valida el tamaño y SHA-256 del archivo Qwen3 antes de cargarlo.
- Tras instalarlo, la inferencia se realiza en el dispositivo.

## Limitaciones

- El selector de cuatro nombres está implementado, pero solo Qwen3 tiene descarga verificada en esta revisión.
- No existe todavía descarga de Phi-4, Llama 3.2 ni Gemma 3.
- El cambio entre modelos requiere un archivo instalado y validado; por ahora, cambiar a los otros dos solo guarda la selección y muestra que el instalador aún no está disponible.
- Preferencias de voz se almacenan, pero el motor de voz y la clonación de voz no están integrados.
- Llamadas, reuniones y correo aún necesitan conectores externos autorizados.
- No declarar memoria SQLCipher integrada en Android hasta verificar interoperabilidad con el servicio local.

## Build

El workflow `.github/workflows/mi-doble-android.yml` ejecuta pruebas JVM, compila el runtime nativo y ensambla el APK Android. Para distribuir builds actualizables debe mantenerse el mismo certificado de firma de beta.
