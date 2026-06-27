package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.data.importing.AuthoringDraftCompiler
import com.estudio.antiprocrastinacion.app.data.importing.AuthoringDraftValidator
import com.estudio.antiprocrastinacion.app.model.authoring.AuthoringCourseDraftDto
import com.estudio.antiprocrastinacion.app.model.authoring.AuthoringDraftPackageDto
import com.estudio.antiprocrastinacion.app.model.authoring.AuthoringItemDraftDto
import com.estudio.antiprocrastinacion.app.model.authoring.AuthoringNodeDraftDto
import com.estudio.antiprocrastinacion.app.model.authoring.AuthoringOptionDraftDto
import com.estudio.antiprocrastinacion.app.model.authoring.AuthoringOutcomeDraftDto
import com.estudio.antiprocrastinacion.app.model.authoring.AuthoringUnitDraftDto
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.NodeType
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AuthoringDraftCompilerTest {
    private val compiler = AuthoringDraftCompiler(AuthoringDraftValidator())

    @Test
    fun `valid draft compiles deterministic ids and calculated runtime fields`() {
        val result = compiler.compile(validDraft(), generatedAt = 10L)
        val contentPackage = result.contentPackage!!

        assertThat(result.report.structuralErrors).isEmpty()
        assertThat(contentPackage.packageId).isEqualTo("eth_moderno")
        assertThat(contentPackage.courses.single().courseId).isEqualTo("ethereum")
        assertThat(contentPackage.units.single().unitId).isEqualTo("ethereum__bloque_0")
        assertThat(contentPackage.outcomes.single().outcomeId).isEqualTo("ethereum__bloque_0__contrato")
        assertThat(contentPackage.nodes.single().nodeId).isEqualTo("ethereum__bloque_0__ethereum_como_protocolo")
        val coreItem = contentPackage.items.single { it.itemId == "ethereum__bloque_0__ethereum_como_protocolo__core_mcq" }
        assertThat(contentPackage.nodes.single().surfaceEasyReady).isTrue()
        assertThat(contentPackage.nodes.single().surfaceEasyItemCount).isEqualTo(4)
        assertThat(coreItem.correctAnswer).isEqualTo("Un protocolo que mantiene un estado global verificable")
        assertThat(coreItem.allowedSurfaces).contains(Surface.SOCIAL_GATE)
    }

    @Test
    fun `same stable keys preserve ids when wording changes`() {
        val original = compiler.compile(validDraft(), generatedAt = 10L).contentPackage!!
        val changed =
            validDraft().copy(
                units =
                    validDraft().units.map { unit ->
                        unit.copy(
                            nodeDrafts =
                                unit.nodeDrafts.map { node ->
                                    node.copy(
                                        title = "Ethereum como protocolo editado",
                                        items = node.items.map { item -> item.copy(stem = "${item.stem} editada") },
                                    )
                                },
                        )
                    },
            )
        val recompiled = compiler.compile(changed, generatedAt = 20L).contentPackage!!

        assertThat(recompiled.nodes.single().nodeId).isEqualTo(original.nodes.single().nodeId)
        assertThat(recompiled.items.map { it.itemId }).containsExactlyElementsIn(original.items.map { it.itemId }).inOrder()
    }

    @Test
    fun `draft rejects invalid pedagogical references`() {
        val broken =
            validDraft().copy(
                units =
                    validDraft().units.map { unit ->
                        unit.copy(
                            nodeDrafts =
                                unit.nodeDrafts.map { node ->
                                    node.copy(
                                        items =
                                            listOf(
                                                node.items.first().copy(
                                                    coversMustKnow = listOf("missing"),
                                                    correct = "missing_option",
                                                ),
                                                node.items.first().copy(
                                                    itemKey = "trap_without_error",
                                                    roleHint = ItemRole.TRAP,
                                                    targetsCommonErrors = emptyList(),
                                                ),
                                            ),
                                    )
                                },
                        )
                    },
            )
        val result = compiler.compile(broken, generatedAt = 10L)

        assertThat(result.contentPackage).isNull()
        assertThat(result.report.structuralErrors.map { it.code }).containsAtLeast(
            "draft_item_covers_unknown:ethereum_como_protocolo:core_mcq",
            "draft_choice_correct_missing:ethereum_como_protocolo:core_mcq",
            "draft_trap_without_error:ethereum_como_protocolo:trap_without_error",
        )
    }

    @Test
    fun `compiler keeps non strict friction one out of external surfaces`() {
        val contentPackage = compiler.compile(validDraft(), generatedAt = 10L).contentPackage!!
        val fillOneWord = contentPackage.items.single { it.format == ItemFormat.FILL_ONE_WORD }
        val boss = contentPackage.items.single { it.itemRole == ItemRole.BOSS }
        val rescue = contentPackage.items.single { it.itemRole == ItemRole.RESCUE }

        assertThat(fillOneWord.allowedSurfaces).doesNotContain(Surface.SOCIAL_GATE)
        assertThat(fillOneWord.allowedSurfaces).doesNotContain(Surface.NOTIFICATION)
        assertThat(fillOneWord.allowedSurfaces).doesNotContain(Surface.BACK_MICRO)
        assertThat(boss.allowedSurfaces).containsExactly(Surface.IN_APP_DEEP)
        assertThat(rescue.allowedSurfaces).contains(Surface.SOCIAL_GATE)
        assertThat(rescue.allowedSurfaces).doesNotContain(Surface.NOTIFICATION)
    }

    @Test
    fun `compiler resolves choice correct option case insensitively when unique`() {
        val base = validDraft()
        val draft =
            base.copy(
                units =
                    base.units.map { unit ->
                        unit.copy(
                            nodeDrafts =
                                unit.nodeDrafts.map { node ->
                                    node.copy(
                                        items =
                                            node.items.map { item ->
                                                if (item.itemKey == "core_mcq") item.copy(correct = "B") else item
                                            },
                                    )
                                },
                        )
                    },
            )

        val contentPackage = compiler.compile(draft, generatedAt = 10L).contentPackage!!
        val coreItem = contentPackage.items.single { it.itemId.endsWith("__core_mcq") }

        assertThat(coreItem.correctAnswer).isEqualTo("Un protocolo que mantiene un estado global verificable")
        assertThat(coreItem.options.single { it.id == "b" }.isCorrect).isTrue()
    }

    @Test
    fun `compiler reports detailed warnings for uncovered must know and weak easy surface`() {
        val base = validDraft()
        val draft =
            base.copy(
                units =
                    base.units.map { unit ->
                        unit.copy(
                            nodeDrafts =
                                unit.nodeDrafts.map { node ->
                                    node.copy(
                                        mustKnow = node.mustKnow + "gas_no_es_herramienta",
                                        items = listOf(node.items.first()),
                                    )
                                },
                        )
                    },
            )

        val result = compiler.compile(draft, generatedAt = 10L)
        val coverageWarning =
            result.report.authoringWarnings.single {
                it.code == "draft_node_must_know_uncovered:ethereum_como_protocolo:gas_no_es_herramienta"
            }
        val surfaceWarning =
            result.report.authoringWarnings.single {
                it.code == "draft_node_surface_easy_insufficient:ethereum_como_protocolo"
            }

        assertThat(result.report.structuralErrors).isEmpty()
        assertThat(result.contentPackage).isNotNull()
        assertThat(coverageWarning.path).isEqualTo("$.units[0].nodeDrafts[0].mustKnow[3]")
        assertThat(coverageWarning.expected).contains("coversMustKnow")
        assertThat(coverageWarning.hint).contains("no elimines")
        assertThat(surfaceWarning.path).isEqualTo("$.units[0].nodeDrafts[0].items")
        assertThat(surfaceWarning.actual).isEqualTo("1")
    }
}

private fun validDraft(): AuthoringDraftPackageDto =
    AuthoringDraftPackageDto(
        packageKey = "eth_moderno",
        sourceRefs = listOf("curso://eth/bloque_0"),
        course = AuthoringCourseDraftDto("ethereum", "Ethereum moderno conceptual"),
        units =
            listOf(
                AuthoringUnitDraftDto(
                    unitKey = "bloque_0",
                    title = "Bloque 0",
                    outcomes = listOf(AuthoringOutcomeDraftDto("contrato", "Explicar el contrato pedagógico")),
                    nodeDrafts =
                        listOf(
                            AuthoringNodeDraftDto(
                                nodeKey = "ethereum_como_protocolo",
                                title = "Ethereum como protocolo",
                                coreClaim = "Ethereum es un protocolo que mantiene un estado global verificable.",
                                nodeTypeHint = NodeType.CONCEPT,
                                outcomeKeys = listOf("contrato"),
                                facets =
                                    listOf(
                                        FacetType.DEFINICION_FUNCIONAL,
                                        FacetType.DIFERENCIA_ENTRE_CONCEPTOS,
                                        FacetType.ERROR_TIPICO,
                                    ),
                                mustKnow =
                                    listOf(
                                        "protocolo_estado_global",
                                        "no_es_app",
                                        "no_es_inversion",
                                    ),
                                commonErrors =
                                    listOf(
                                        "Confundir Ethereum con una aplicación",
                                        "Pensar que Ethereum es solo una inversión",
                                    ),
                                minimumMasteryDefinition = "Distingue Ethereum como protocolo y no como app.",
                                items =
                                    listOf(
                                        choiceItem("core_mcq", ItemRole.CORE),
                                        trueFalseItem("tf_variant"),
                                        choiceItem("trap_app", ItemRole.TRAP, listOf("Confundir Ethereum con una aplicación")),
                                        choiceItem("variant_mcq", ItemRole.VARIANT),
                                        fillItem("fill_basic"),
                                        rescueItem("rescue_entry"),
                                        bossItem("boss"),
                                    ),
                            ),
                        ),
                ),
            ),
    )

private fun choiceItem(
    key: String,
    role: ItemRole,
    errors: List<String> = emptyList(),
): AuthoringItemDraftDto =
    AuthoringItemDraftDto(
        itemKey = key,
        format = ItemFormat.MULTIPLE_CHOICE,
        facet = if (role == ItemRole.TRAP) FacetType.ERROR_TIPICO else FacetType.DEFINICION_FUNCIONAL,
        roleHint = role,
        stem = "¿Cuál describe mejor Ethereum?",
        options =
            listOf(
                AuthoringOptionDraftDto("a", "Una aplicación para enviar dinero"),
                AuthoringOptionDraftDto("b", "Un protocolo que mantiene un estado global verificable"),
                AuthoringOptionDraftDto("c", "Un exchange descentralizado"),
            ),
        correct = "b",
        feedback = "Ethereum se define como protocolo.",
        coversMustKnow = listOf("protocolo_estado_global"),
        targetsCommonErrors = errors,
    )

private fun trueFalseItem(key: String): AuthoringItemDraftDto =
    AuthoringItemDraftDto(
        itemKey = key,
        format = ItemFormat.TRUE_FALSE,
        facet = FacetType.DIFERENCIA_ENTRE_CONCEPTOS,
        roleHint = ItemRole.VARIANT,
        stem = "Ethereum se estudia acá como inversión.",
        correct = "false",
        feedback = "En este curso se estudia como protocolo.",
        coversMustKnow = listOf("no_es_inversion"),
    )

private fun fillItem(key: String): AuthoringItemDraftDto =
    AuthoringItemDraftDto(
        itemKey = key,
        format = ItemFormat.FILL_ONE_WORD,
        facet = FacetType.DEFINICION_FUNCIONAL,
        roleHint = ItemRole.VARIANT,
        stem = "Ethereum mantiene un estado global ____.",
        correct = "verificable",
        feedback = "La palabra clave es verificable.",
        coversMustKnow = listOf("protocolo_estado_global"),
    )

private fun rescueItem(key: String): AuthoringItemDraftDto =
    AuthoringItemDraftDto(
        itemKey = key,
        format = ItemFormat.MULTIPLE_CHOICE,
        facet = FacetType.DEFINICION_FUNCIONAL,
        roleHint = ItemRole.RESCUE,
        stem = "Entrada suave: Ethereum es...",
        options =
            listOf(
                AuthoringOptionDraftDto("a", "Un protocolo"),
                AuthoringOptionDraftDto("b", "Un exchange"),
            ),
        correct = "a",
        feedback = "Sirve como entrada de baja fricción.",
        coversMustKnow = listOf("protocolo_estado_global"),
    )

private fun bossItem(key: String): AuthoringItemDraftDto =
    AuthoringItemDraftDto(
        itemKey = key,
        format = ItemFormat.ONE_SENTENCE_EXPLANATION,
        facet = FacetType.DIFERENCIA_ENTRE_CONCEPTOS,
        roleHint = ItemRole.BOSS,
        stem = "Explicá Ethereum como protocolo en una oración.",
        correct = "Ethereum mantiene reglas y estado global verificable.",
        feedback = "Debe aparecer protocolo y estado verificable.",
        coversMustKnow = listOf("protocolo_estado_global"),
    )
