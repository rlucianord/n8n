# Mi Doble — servicio local

Mi Doble es un servicio local independiente de n8n. La información personal y la memoria permanecen en el dispositivo en producción.

## Estado

La base incluye API local, memoria persistente y cifrado SQLCipher. El cliente Android incluye diagnóstico de dispositivo y una primera ruta de instalación de Qwen3. El motor Android aún necesita pruebas de inferencia en dispositivos físicos.

La rama de acciones añade configuración persistente de voz, modelo activo y permisos para llamadas, reuniones y correo. Las políticas empiezan desactivadas y las evaluaciones quedan auditadas. Esta capa no sustituye los conectores externos: las operaciones devuelven explícitamente `not_connected` cuando no existe integración real.

## Documentación

- [Centro de acciones, voz e integraciones](./docs/AGENCY_ACTIONS.md)
- [Catálogo de modelos](./docs/MODEL_CATALOG.md)
- [Runtime Android local](./docs/ANDROID_LOCAL_RUNTIME.md)
- [Integración n8n](./docs/N8N_INTEGRATION.md)

## Requisitos y ejecución

Python 3.12+, SQLCipher y las dependencias fijadas en `requirements.txt`. Configurar una clave privada `MIDOBLE_DB_KEY` de al menos 32 caracteres en `.env`. Ejecutar solo en loopback hasta incorporar autenticación.

```sh
cd mi-doble
python3 -m venv .venv
. .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements-dev.txt
cp .env.example .env
uvicorn app.main:app --host 127.0.0.1 --port 8765
pytest -q
```

No guardar credenciales, tokens, datos personales ni modelos en GitHub.
