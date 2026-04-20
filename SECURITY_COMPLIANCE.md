# Intelligent Parent Eye - Security & Compliance Notes

## Data Handling Boundaries

- The prototype collects only operational data required for parental safety controls.
- Sensitive actions are evented with minimal fields (type, severity, timestamp, limited metadata).
- Parent-child access boundaries are enforced by Firestore path design:
  - `parents/{parentId}/children/{childId}`
  - child events under `parents/{parentId}/children/{childId}/events`

## Transport and Storage

- Network transport uses Firebase TLS channels.
- Local child-device secrets/IDs are stored with Android encrypted preferences.
- No plaintext credentials are stored in the mobile source code.

## Prototype Limitations

- Non-root Android APIs cannot guarantee complete traffic inspection across all apps.
- VPN-based protection may require user consent and can be bypassed by advanced app behaviors.
- Accessibility fallback URL blocking remains active to increase practical coverage.
- Rooted or heavily modified devices may bypass controls.

## Retention and Minimization

- Event retention is configurable per child profile (`eventRetentionDays`).
- Dashboard and backend should periodically prune events older than retention settings.
- Academic PoC scope: avoid full message content persistence when possible; prefer risk labels and metadata.

## Recommended Firestore Policy Model (to be enforced in deployment)

- Parents can read/write only their own subtree.
- Child app writes are scoped to linked child identities via a trusted link document.
- Events are append-only from device side; parent can read and manage archival views.

## Ethical Compliance Checklist

- Use explicit consent for monitoring features.
- Request Android dangerous permissions with clear, user-facing explanations.
- Keep a visible policy describing what is monitored and why.
- Support data deletion requests for project demos/testing accounts.
