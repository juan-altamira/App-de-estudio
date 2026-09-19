# AuthoringDraft v1

## Purpose

`AuthoringDraftPackage` is the recommended input contract for AI-assisted content creation.

The app still imports `ContentPackageDto` as the final internal content format, but AI authoring should not be asked to produce that final runtime JSON directly.

The stable flow is:

```text
human source material
  -> AI produces AuthoringDraftPackage
  -> local compiler produces ContentPackageDto
  -> final validator rejects structural inconsistencies
  -> importer performs idempotent upsert
```

## Contract marker and aliases

The canonical authoring contract is still `AuthoringDraftPackage`.

The import preflight may accept optional root metadata:

```json
{
  "schema": "authoring_draft",
  "packageKey": "ethereum_moderno",
  "course": { "...": "..." },
  "units": []
}
```

Recognized authoring aliases are:
- `authoring_draft`
- `AuthoringDraftPackage`
- `AuthoringDraftPackageDto`
- `QuestionDraftPackage`

`QuestionDraftPackage` is only a compatibility alias for a full node-centered `AuthoringDraftPackage` shape. A flat package with root `questions[]` must be rejected. The compiler must not convert flat questions into nodes by guessing missing facets, must-know coverage, common errors or roles.

Recognized content aliases are:
- `content_package`
- `ContentPackage`
- `ContentPackageDto`

The preflight strips only recognized `schema` metadata before strict authoring parsing. Unknown keys remain fatal.

For final `ContentPackageDto` file imports, the root may also include optional import metadata:

```json
{
  "schema": "content_package",
  "importMode": "additive",
  "packageId": "ethereum_unit_2_increment",
  "courses": []
}
```

`importMode` is not a `ContentPackageDto` field and must not be persisted as content. It is stripped during preparation and only selects the validation/import profile.

Supported `importMode` values:
- absent, `full`, `republish` or `replace`: full-package semantics; missing same-origin nodes in affected courses become `archived_candidate`
- `additive`, `incremental` or `append`: additive semantics; incoming nodes are upserted and sibling nodes absent from the file are not archived

Use additive mode for files that add a new unit or questions to an existing course through the `Archivo` button.

## Boundary

The AI may propose pedagogical semantics:
- course and unit drafts
- outcomes
- nodes
- facets
- must-know coverage
- common errors
- item intent, role and feedback

The AI must not decide runtime mechanics:
- final timestamps
- content hash
- `allowedSurfaces`
- `frictionLevel`
- `surfaceEasyReady`
- `surfaceEasyItemCount`
- final `correctAnswer` normalization
- final option `isCorrect`
- scheduler behavior
- progress identity beyond stable keys

## Identity

Drafts use stable keys, not final IDs:
- `packageKey`
- `courseKey`
- `unitKey`
- `outcomeKey`
- `nodeKey`
- `itemKey`

The compiler derives final IDs deterministically:
- `courseId = courseKey`
- `unitId = courseKey__unitKey`
- `outcomeId = unitId__outcomeKey`
- `nodeId = unitId__nodeKey`
- `itemId = nodeId__itemKey`

Changing wording, feedback or options while keeping the same keys preserves final identity and existing progress.

Changing a key creates a new identity.

## Required shape

The draft is node-centered. It must not be a flat `questions[]` list.

Minimal shape:

```json
{
  "packageKey": "ethereum_moderno",
  "course": {
    "courseKey": "ethereum",
    "title": "Ethereum moderno conceptual"
  },
  "units": [
    {
      "unitKey": "bloque_0",
      "title": "Bloque 0",
      "outcomes": [
        {
          "outcomeKey": "contrato",
          "title": "Explicar el contrato pedagogico"
        }
      ],
      "nodeDrafts": [
        {
          "nodeKey": "ethereum_como_protocolo",
          "title": "Ethereum como protocolo",
          "coreClaim": "Ethereum es un protocolo que mantiene un estado global verificable.",
          "nodeTypeHint": "CONCEPT",
          "outcomeKeys": ["contrato"],
          "facets": [
            "DEFINICION_FUNCIONAL",
            "DIFERENCIA_ENTRE_CONCEPTOS",
            "ERROR_TIPICO"
          ],
          "mustKnow": ["estado_global_verificable"],
          "commonErrors": ["Confundir Ethereum con una aplicacion"],
          "minimumMasteryDefinition": "Distingue Ethereum como protocolo y no como app.",
          "items": [
            {
              "itemKey": "core_mcq",
              "format": "MULTIPLE_CHOICE",
              "facet": "DEFINICION_FUNCIONAL",
              "roleHint": "CORE",
              "stem": "Cual describe mejor Ethereum en este curso?",
              "options": [
                {"key": "a", "text": "Una aplicacion para enviar dinero"},
                {"key": "b", "text": "Un protocolo que mantiene un estado global verificable"}
              ],
              "correct": "b",
              "feedback": "Ethereum se define como protocolo, no como app.",
              "coversMustKnow": ["estado_global_verificable"]
            }
          ]
        }
      ]
    }
  ]
}
```

## Strict validation

Authoring draft parsing is strict:
- unknown keys are fatal
- unknown `schema` aliases are fatal
- invalid stable keys are fatal
- duplicated unit, node or item keys are fatal
- missing outcomes, nodes, facets, must-know or common errors are fatal where required
- `coversMustKnow` must reference an existing node `mustKnow`
- `targetsCommonErrors` must reference an existing node `commonErrors`
- `TRAP` items must target at least one common error
- item `facet` must be declared by the node
- choice formats require 2 to 6 options and exactly one `correct` key
- `TRUE_FALSE` must not bring options and must use a true/false answer
- open or explanation formats must not bring options

The compiler does not invent deep pedagogy. If the draft lacks node semantics, coverage, facet or role intent, the correct behavior is rejection.

The preflight may do narrow mechanical normalization for enum tokens only: casing, spaces and hyphens may be normalized to the existing enum names. Stable identity keys are never normalized silently.

Validation messages must carry enough structured detail for UI/debugging:
- machine code
- JSON path when known
- expected contract/value
- received value or shape
- actionable hint

## Surface and friction rules

The compiler owns runtime surface assignment:
- strict friction-1 formats may be used for `SOCIAL_GATE`, `BACK_MICRO`, `NOTIFICATION`, `ALARM` and `WIDGET`
- non-strict formats stay in in-app study surfaces
- `RESCUE` compiles as auxiliary low-friction content and must not be counted as real scheduler debt
- `INTEGRATION` and `BOSS` compile for deep/manual study, not external quick hooks
- `surfaceEasyReady` is calculated from real strict easy items
- `surfaceEasyItemCount` is calculated, never authored by the AI

Legacy `ContentPackageDto` imports remain supported, including explicit additive file imports, but new AI-generated content should use `AuthoringDraftPackage`.

## Reviewed editable text import

The app also supports a local reviewed editable import path for natural-language notes or pasted question lists:

```text
natural text
  -> local conservative extractor
  -> reviewed editable screen with one source-text input
  -> ReviewedEditableImportDraft
  -> ReviewedQuestionBankCompiler
  -> ContentPackageDto
  -> import with REVIEWED_QUESTION_BANK validation profile
```

This path is deliberately not a schema alias and is not accepted by raw JSON preflight. It is a UI workflow where the user starts with one source-text input, then reviews and edits the detected draft before compilation. Corrections may happen either by changing the source text and reanalyzing, by editing the detected course/unit/question fields directly, by adding questions, or by removing questions.

The detected draft is a user-facing review/edit screen, not a technical inspector. It must show editable controls for:
- course title
- unit title
- add/remove question controls per unit/question
- question text
- answer format in plain language
- options when present, including option text and which option is correct
- expected answer
- explanation or missing-explanation state
- corrected confusion only when it is authored text the user needs to verify

Technical data belongs in diagnostics only: generated or explicit stable IDs, source refs, parser line numbers, internal roles, schema/package names, underscore keys and compiler/import metadata. The compiler must consume the edited `ReviewedEditableImportDraft`, not the original detected text snapshot.

Preferred source-text data:
- optional explicit course title line: `Curso:` or `Materia:`; if absent, the extractor infers a visible course title from the text and warns
- optional stable course ID line: `ID curso:`, `course_id:` or `courseKey:`; if absent, the extractor generates a slug and warns
- optional explicit unit title lines: `Unidad:`, `Tema:`, `Modulo:` or `Seccion:`; if absent, the extractor creates one inferred unit and warns
- optional stable unit ID line after each unit: `ID unidad:`, `unit_id:`, `unitKey:` or `section_id:`; if absent, the extractor generates a slug and warns
- optional source reference line: `Fuente:`, `Referencia:` or `Source:`
- questions under a unit, preferably inside `--- PREGUNTA ---` / `--- FIN ---`
- optional question stable ID: `[id: ...]`, `ID:` or `ID pregunta:`; if absent, the extractor generates a slug from the prompt and warns because wording changes can change identity
- question prompt from `Pregunta:`, `P:`, `Q:`, `N.`/`N)`, or a guarded weak `?` line only inside an evaluable zone with answer evidence
- options as `A) ...` or `A. ...` through `F) ...`; `*` or visual check marks may mark the single correct option
- answer through `Correcta:`, `Respuesta:`, `Respuesta correcta:`, `Solucion:`, `Answer:` or `Correct answer:`
- feedback through `Explicacion:`, `Justificacion:`, `Feedback:`, `Porque:` or `Por que:`; if absent, the extractor generates only mechanical feedback (`Respuesta correcta: ...`) and warns
- corrected confusion when the study role is `Trampa`

If the source text is badly presented prose with no explicit question blocks, the extractor must still produce a reviewable draft when it can do so faithfully:
- split real prose into paragraphs
- create one `Ver respuesta` question per meaningful paragraph
- use the paragraph itself as the correct answer
- generate only mechanical feedback from that paragraph
- infer course/unit titles from visible text with warnings
- never invent options, trap role, integration role, concepts, facts, common errors or coverage that are not visible in the source text

The extractor is conservative:
- a bare question mark does not create a question outside an evaluable zone
- evaluable zone markers are `Mini-examen`, `Examen`, `Preguntas`, `Evaluacion`, `Cuestionario` and `Responde`
- a question before the first explicit unit is assigned to an inferred unit with warnings instead of being dropped
- explicit duplicate IDs are blocking errors
- generated duplicate question or unit slugs get deterministic suffixes and warnings
- `CHOOSE_FALSE_STATEMENT` does not imply study role `Trampa`
- `Trampa` requires explicit `Confusion:`, `Error tipico:` or `Trampa:` text

Study role labels in this screen are normal authored question roles:
- `Normal`
- `Variante`
- `Trampa`
- `Integración`
- `Profunda`

The editable question-bank compiler must not generate `RESCUE`. Rescue remains an auxiliary fallback after failures, not a normal imported question role.

The manual course builder uses this reviewed question-bank profile and must allow a unit with at least one complete question of any supported format. The four-item strict friction-1 target is not a save gate: it only determines external-surface readiness. A normal `REVEAL_ANSWER` compiles to real `IN_APP_QUICK` plus `IN_APP_DEEP` debt and therefore remains eligible for `Tarjetas pendientes`, even when the unit has no strict easy opener.

When the manual builder targets an existing unit, the compilation context keeps the exact existing course/unit identity and metadata but assigns a fresh outcome, node and item namespace to the new batch. The import remains additive under `REVIEWED_QUESTION_BANK`: existing questions are not included in the incoming batch, overwritten, archived or deleted, and their progress remains attached to their original IDs.

The compiler derives deterministic content IDs:
- `courseId = courseKey`
- `unitId = courseKey__unitKey`
- `outcomeId = unitId__resolver_preguntas`
- `nodeId = unitId__contenido`
- `itemId = nodeId__questionKey`

The compiler may derive:
- final item format
- item role mapping, excluding `RESCUE`
- friction level
- allowed surfaces
- cooldown
- difficulty seed
- conservative `mustKnow` lines from completed question and answer fields
- `surfaceEasyReady`
- `surfaceEasyItemCount`

The compiler may not derive missing user-facing question fields. If prompt, correct answer, choice correctness, or trap confusion is incomplete, the editable validator must block approval. Stable keys may be generated only by deterministic slug fallback with visible warnings; explicit IDs remain the only stable identity guarantee across wording edits.

`ContentPackageDto` remains the final internal/importer contract and must not contain a content/import profile field. The profile lives only as a validation/import parameter.
