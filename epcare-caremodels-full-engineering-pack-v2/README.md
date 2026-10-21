# ePCare Care Models — Full Engineering Handoff

**Baseline date:** 2026-10-21

Read in order:
1. `01-REQUIREMENTS.md` — authoritative product behavior and acceptance criteria.
2. `02-ARCHITECTURE-AND-DESIGN.md` — table design, JSON hierarchy, runtime sequence, REST contract, UI structure.
3. `03-AGENT-INSTRUCTIONS.md` — implementation order, guardrails, backend/frontend agent tasks, verification.

These documents **supersede the earlier facilities-overlay baseline**. Every facility has its own OpenMRS instance, so no per-facility activation layer is needed. Master/model privileges are outside JSON. Only ACTIVE enrolled and authorized Care Models appear as patient tabs; data loads lazily on selection.

The illustrative JSON architecture in `02` must be made into a schema+validator as the first implementation workstream. Do not treat placeholder UUIDs, Java interface names, or OpenMRS extension slots as pre-validated against a specific existing deployment.
