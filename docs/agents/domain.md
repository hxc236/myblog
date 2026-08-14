# Domain Docs

This repository uses a single domain context.

## Before exploring

Read these when they exist:

- `CONTEXT.md` at the repository root.
- Relevant decisions under `docs/adr/`.

If they do not exist, proceed silently. The domain-modeling workflow creates them lazily when terminology or architectural decisions are resolved.

## Layout

```text
/
├── CONTEXT.md
├── docs/
│   └── adr/
├── backend/
└── myblogweb/
```

## Vocabulary

Use canonical terms from `CONTEXT.md` in issue titles, specifications, tests, and implementation discussions. If a necessary concept is missing or ambiguous, resolve it through domain modeling before adding it.

## Architecture decisions

If proposed work contradicts an existing ADR, identify the conflict explicitly instead of silently overriding the earlier decision.
