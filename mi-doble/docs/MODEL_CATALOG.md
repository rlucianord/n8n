# Catálogo de modelos de Mi Doble

El catálogo visible ofrece cinco modelos que el usuario puede seleccionar:

1. Phi-4
2. Qwen 3
3. Gemma 3
4. Llama 3.2
5. SmolLM3

Los nombres se muestran sin detalles técnicos de cuantización. La aplicación debe descargar variantes cuantizadas compatibles con el dispositivo y ejecutar los modelos localmente, sin enviar las conversaciones a un servicio externo.

## SmolLM3: candidato aprobado para integración técnica

SmolLM3-3B tiene una variante GGUF Q4_K_M de aproximadamente 1.92 GB, licencia Apache 2.0, soporte multilingüe que incluye español e integración documentada con llama.cpp. Por tamaño y arquitectura es un candidato razonable para Mi Doble en teléfonos con memoria suficiente.

La disponibilidad del archivo no garantiza que funcione en todos los teléfonos. Antes de habilitar su descarga general, la aplicación debe comprobar compatibilidad con la versión de llama.cpp integrada, memoria disponible, plantilla de conversación y carga/inferencia real en dispositivo. Debe rechazarse la instalación cuando el dispositivo no tenga recursos suficientes.

## Reglas de producto

- El usuario puede elegir y cambiar el modelo en cualquier momento desde Ajustes.
- El usuario puede descargar un modelo nuevo cuando esté disponible para su dispositivo.
- Descargar un modelo no lo convierte automáticamente en el modelo activo.
- Conservar los modelos descargados, perfil, conversaciones, memoria y preferencias durante las actualizaciones.
- Descargar únicamente el modelo que el usuario seleccione.
- Validar licencia, URL oficial, revisión fijada, tamaño, hash SHA-256, arquitectura y memoria disponible antes de habilitar una descarga.
- No reemplazar el modelo activo hasta que el nuevo modelo esté descargado, verificado y haya superado una prueba de carga e inferencia.
- Si la carga falla, volver al modelo activo anterior.
- No instalar todos los adaptadores de inferencia ni dependencias pesadas en el paquete base.
- Separar la descarga de modelos de la actualización de la aplicación.
- Mostrar los modelos incompatibles como no disponibles para ese dispositivo, sin ofrecer una descarga que no pueda ejecutarse.

## Estado de implementación

El catálogo y la política de selección están definidos. El descargador y la carga intercambiable de Android todavía deben implementarse y probarse para cada familia. En la versión actual, el descargador verificado disponible es Qwen 3; Phi-4, Gemma 3, Llama 3.2 y SmolLM3 no deben marcarse como instalables hasta incorporar sus artefactos concretos y superar las pruebas. SmolLM3 queda aprobado como candidato técnico, condicionado a esas pruebas.

## Compatibilidad

El nombre comercial no identifica por sí solo un archivo ejecutable. Cada variante requiere una ficha con URL fijada, revisión, licencia, tamaño, SHA-256, arquitectura, plantilla de conversación y límites medidos. La aplicación debe evaluar RAM disponible y almacenamiento antes de descargar y antes de cargar el modelo.
