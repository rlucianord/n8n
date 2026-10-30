# Mi Doble — Centro de acciones y voz

## Objetivo

Permitir que el usuario configure qué tareas puede preparar o ejecutar Mi Doble, con qué voz debe hablar y qué aprobaciones son necesarias. La voz elegida por el usuario —incluida una voz personalizada cuya creación haya autorizado— es una preferencia válida. La aplicación debe identificar claramente que quien interviene es un asistente de IA; la identificación no puede desactivarse.

## Estado de esta entrega

Esta entrega incorpora la primera capa persistente de configuración y evaluación de permisos en el servicio local:

- Preferencias de voz, uso en llamadas y reuniones y modelo activo.
- Políticas separadas para llamadas, reuniones y correo.
- Las acciones están desactivadas inicialmente.
- La evaluación distingue entre denegado, pendiente de aprobación y autorizado.
- Las decisiones quedan en el registro de auditoría.
- Una acción autorizada todavía devuelve `execution=not_connected` hasta que exista un conector real para el servicio externo.

No se debe presentar una autorización como si la llamada, reunión o envío se hubiera ejecutado.

## API incorporada

- `GET /agency/settings`
- `PUT /agency/settings`
- `GET /agency/policies`
- `PUT /agency/policies/{action}`
- `POST /agency/decisions/evaluate`

Acciones configurables:

- `phone.answer`, `phone.reject`
- `meeting.join`, `meeting.speak`
- `email.read`, `email.draft`, `email.send`

Las acciones empiezan desactivadas. El usuario puede exigir confirmación para cada una. En esta primera fase, el endpoint evalúa la política y registra la decisión; no ejecuta integraciones externas.

## Voz

Configuraciones previstas:

- Voz del dispositivo.
- Voz personalizada del usuario, únicamente después de autorización expresa para crearla y almacenarla.
- Voz alternativa elegida por el usuario.
- Uso independiente en llamadas y reuniones.
- Controles de velocidad, volumen y tono en la interfaz de voz.

La capa de configuración no equivale a un motor de clonación de voz ni a un sistema telefónico. La integración de voz personalizada requiere un proveedor o motor compatible, consentimiento verificable y controles de borrado.

## Integraciones necesarias para ejecución real

- Correo: OAuth con Gmail o Microsoft 365, scopes mínimos, lectura y borradores primero; envío solo tras activar la política.
- Reuniones: integración autorizada con el calendario y el proveedor de videoconferencia; el audio y la participación hablada requieren permisos del sistema y aviso a los participantes.
- Llamadas: Android CallScreeningService/roles permitidos para filtrado y un proveedor de telefonía compatible para contestación automatizada. Android no permite que una aplicación ordinaria capture o controle libremente todas las llamadas.
- Voz: motor TTS compatible con voz seleccionada y proveedor autorizado para voz personalizada.

Las credenciales se almacenarán fuera del repositorio, protegidas por almacenamiento seguro. No se incluirán secretos, tokens OAuth ni grabaciones en Git.

## Seguridad

El backend actual solo debe ejecutarse en loopback. Antes de exponerlo a otros dispositivos se necesita autenticación, autorización por usuario, protección CSRF donde corresponda, límites de tasa, cifrado de credenciales y revisión de amenazas. El sistema debe identificar al asistente como IA y respetar los permisos y avisos exigidos por la plataforma y la ley.
