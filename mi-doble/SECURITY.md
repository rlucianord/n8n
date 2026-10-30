# Security policy

- Never commit `.env`, database files, model weights, audio, recordings, personal documents, or real conversation exports.
- The repository is public; assume every committed byte is public.
- Use a locally generated random SQLCipher key and keep a protected backup of it. Losing the key means losing access to the encrypted database.
- The API binds to loopback only in this milestone.
- Do not expose the API to a LAN or internet until authentication, authorization, rate limits, and threat-model review are implemented.
- No action involving phone calls, meetings, messages, purchases, or disclosure of personal information should run without a clear user policy and required confirmation.
- The assistant must disclose that it is an AI assistant when communicating with other people.
- Report vulnerabilities privately to the repository owner; do not publish secrets or exploit details in issues.
