# cloud-itonami-isic-1062: Starches and Starch Products Coordination Actor

**ISIC Rev. 5 1062** — Manufacture of Starches and Starch Products

A distributed actor for autonomous, compliant coordination of starches-and-starch-products manufacturing plant operations: raw-material intake (corn/potato/cassava/wheat) → steeping → extraction → refining → drying → moisture/purity/sulfite-residue/microbial-load/granulation inspection → allergen labeling → finished-product logistics. Sealed LLM advisor; independent Governor enforcement; append-only audit ledger. **Not equipment control.** Steeping-tank/centrifuge/hydrocyclone/dryer operation and food-safety certification authority remain exclusive to licensed starch-plant staff and regulators.

## Scope

This actor coordinates **plant-operations workflow** for starches-and-starch-products manufacturing (native corn/potato/cassava/wheat starch, and related starch products):
- Production batch logging (raw-material intake, extraction/refining parameters, evidence checklist)
- Equipment maintenance scheduling (steeping tanks, centrifuges, hydrocyclones, dryers, magnets)
- Food-safety concern escalation (sulfite-residue contamination, microbial contamination)
- Finished-product shipment coordination

**Out of scope:**
- Direct extraction/refining-line equipment control (plant staff exclusive)
- Food-safety certification authority (human inspector/regulator only)
- Regulatory interpretation (proposals cite jurisdiction specifications; the Governor enforces only published requirements)

## Design

### Governor (Independent Compliance Layer)

The Governor is the separation-of-powers enforcement. It never trusts the advisor's confidence for anything safety- or compliance-relevant, and it always wins over the advisor.

- **Hard HOLD** (un-overridable):
  - Operation outside the closed allowlist (`:op-not-allowed`) — includes any proposal that would touch extraction/refining-line control or food-safety certification
  - Proposal asserting an `:effect` other than `:propose` (`:effect-not-propose`)
  - No jurisdiction citation (`:no-spec-basis`) — can't verify requirements without one
  - Evidence checklist incomplete, or the batch record isn't registered (`:evidence-incomplete`)
  - Finished-product moisture outside the product's safe storage/quality range (`:moisture-out-of-target`)
  - Sulfite (SO2) residue from steeping/bleaching exceeds the product's regulatory action level (`:sulfite-residue-exceeded`)
  - Microbial load (CFU/g) exceeds the product's regulatory action level (`:microbial-load-exceeded`)
  - Purity out of the product's extraction-yield window (`:purity-out-of-range`)
  - Particle size (granulation) out of the product's grade window (`:granulation-out-of-range`)
  - Foreign material detected on the batch's own inspection — metal/stone/glass/insect fragments (`:foreign-material-detected`)
  - Detection-equipment (magnet/metal-detector) calibration overdue (`:detection-equipment-calibration-overdue`)
  - Finished-product weight variance excessive (`:weight-variance-excessive`)
  - Allergen label mismatch — declared allergens don't cover the raw-material formulation, including wheat/gluten cross-contact from shared-line wheat-starch extraction (`:allergen-label-mismatch`)
  - Plant sanitation/pest-control score insufficient (`:sanitation-score-insufficient`)
  - Unresolved food-safety flag (`:food-safety-flag-unresolved`)
  - Batch already processed / shipment already finalized (double-commit guards)
  - `:coordinate-shipment` against a batch that was never registered (`:batch-not-registered`)
- **Escalate** (human sign-off always required):
  - `:log-production-batch` / `:coordinate-shipment` — real actuation events, always require plant-operator sign-off even when the Governor is otherwise clean
  - `:flag-food-safety-concern` — a food-safety concern (sulfite residue, microbial contamination) is never auto-resolved by advisor confidence alone
  - Low advisor confidence (below `governor/confidence-floor`, 0.6)
- **Commit** (advisor proposal approved; Governor clean; not a mandatory-escalation op):
  - Routine, low-stakes proposals only — in this actor's current allowlist that is effectively `:schedule-maintenance` when clean

### Operations (Proposals)

Closed allowlist — the advisor may **only** ever propose these four operation types, all `:effect :propose`:

- **`:log-production-batch`** — Log raw-material intake → extraction → refining → inspection batch into production records (always requires human sign-off)
- **`:schedule-maintenance`** — Propose equipment maintenance for steeping tanks/centrifuges/hydrocyclones/dryers/magnets (routine, low risk)
- **`:flag-food-safety-concern`** — Surface a food-safety or contamination concern (e.g. sulfite residue, microbial contamination); always escalates
- **`:coordinate-shipment`** — Finalize shipment of finished product (always requires human sign-off)

Any proposal for an operation outside this allowlist — most importantly anything that would amount to direct extraction/refining-line control, or food-safety certification — is refused unconditionally by the Governor (`:op-not-allowed`), regardless of advisor confidence.

## Testing

```bash
# Run full test suite
clojure -M:test

# Check code quality
clojure -M:lint

# Run demo simulation
clojure -M:run
```

## Standalone Use

This repo is **forkable outside the workspace**. If cloning standalone (not in the kotoba-lang monorepo), override `:local/root` paths in `deps.edn`:

```clojure
{:deps {io.github.kotoba-lang/langchain {:git/url "https://github.com/kotoba-lang/langchain" :git/tag "v0.1.0"}
        io.github.kotoba-lang/langgraph {:git/url "https://github.com/kotoba-lang/langgraph" :git/tag "v0.1.0"}}}
```

## License

AGPL-3.0-or-later. Forking/contribution welcome; see `CONTRIBUTING.md`.

## Security

Report security issues to the issue tracker or private disclosure; see `SECURITY.md`.

---

Part of **cloud-itonami**: autonomous actor fleet for regulated industries. See [github.com/cloud-itonami](https://github.com/cloud-itonami).
