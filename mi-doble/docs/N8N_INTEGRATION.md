# n8n integration boundary

Mi Doble is a separate local service; do not embed private memory or model execution into n8n's core.

## Intended pattern

1. An n8n workflow receives an event.
2. A policy/approval step decides whether the event may be forwarded.
3. The workflow calls Mi Doble's local API.
4. Mi Doble evaluates its own permissions and performs local inference.
5. Only the minimum response needed by the workflow is returned.

## Important constraints

- This bootstrap currently exposes only `GET /health`; it does not yet expose chat, memory, phone, or Zoom endpoints.
- Do not connect n8n Cloud to a private local service by exposing the service publicly.
- When self-hosting n8n on the same machine, connect through a private local network and add authentication before enabling endpoints beyond health.
- Keep the SQLCipher key inside Mi Doble's private environment. Never place it in an n8n workflow, workflow export, GitHub secret for Codespaces, or node parameter.
- Workflows must disclose that the caller/presenter is an AI assistant where applicable and respect user confirmation rules.
- Phone and Zoom communications cannot be fully offline because their underlying networks/providers are external.

## Planned workflows (not implemented)

- Meeting preparation and agenda briefing
- Meeting notes summarization after user-authorized capture
- Incoming call classification and optional AI-assistant response
- User-approved follow-up drafting

No workflow should impersonate the user or conceal that it is automated.
