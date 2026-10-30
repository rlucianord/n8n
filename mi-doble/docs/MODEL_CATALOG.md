# Catálogo de modelos de Mi Doble

El catálogo visible debe mostrar exactamente tres opciones, sin mostrar cuantización, formatos de archivo ni otros detalles técnicos en la pantalla principal:

1. Phi-4
2. Qwen3
3. Gemma 3

GPT-OSS, Llama 3.2 y SmolLM2 quedan excluidos de este catálogo.

## Reglas de producto

- El usuario puede elegir y cambiar el modelo en cualquier momento desde Ajustes.
- Descargar un modelo no lo convierte automáticamente en el modelo activo.
- Conservar los modelos descargados, perfil, conversaciones, memoria y preferencias durante las actualizaciones.
- Descargar únicamente el modelo que el usuario seleccione.
- Validar licencia, URL oficial, tamaño, hash, arquitectura y memoria disponible antes de habilitar una descarga.
- No reemplazar el modelo activo hasta que el nuevo modelo esté descargado, verificado y haya superado una prueba de carga.
- Si la carga falla, volver al modelo activo anterior.
- No instalar todos los adaptadores de inferencia ni dependencias pesadas en el paquete base.
- Separar la descarga de modelos de la actualización de la aplicación.

## Compatibilidad

El nombre comercial no identifica por sí solo un archivo que se pueda ejecutar en Android. Phi-4 tiene variantes con requisitos de memoria muy distintos; la aplicación debe elegir un artefacto compatible y medido para el dispositivo, sin prometer que el modelo completo cabe en un teléfono de 4 GB. Cada artefacto requiere una ficha con URL fijada, revisión, licencia, tamaño, SHA-256, arquitectura y límites medidos.

El catálogo y la interfaz de selección quedan especificados aquí. El selector multi-modelo, los cuatro descargadores y la carga intercambiable en Android requieren implementación y pruebas de dispositivo antes de declararse terminados.
