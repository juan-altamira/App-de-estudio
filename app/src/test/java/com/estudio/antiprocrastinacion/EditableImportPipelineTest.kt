package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.data.importing.DefaultImportValidator
import com.estudio.antiprocrastinacion.app.data.importing.EditableImportPreparationService
import com.estudio.antiprocrastinacion.app.data.importing.NaturalTextDraftExtractor
import com.estudio.antiprocrastinacion.app.data.importing.ReviewedEditableDraftValidator
import com.estudio.antiprocrastinacion.app.data.importing.ReviewedQuestionBankCompiler
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableCourseDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableImportDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableOptionDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableQuestionDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableUnitDraft
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedQuestionFormat
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedQuestionStudyRole
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EditableImportPipelineTest {
    private val extractor = NaturalTextDraftExtractor()
    private val draftValidator = ReviewedEditableDraftValidator()
    private val compiler = ReviewedQuestionBankCompiler()
    private val importValidator = DefaultImportValidator()

    @Test
    fun `extractor reads one source input with explicit course unit and question ids`() {
        val result =
            extractor.extract(
                """
                Curso: Ethereum
                ID curso: eth
                Unidad: Base
                ID unidad: base
                Fuente: apunte

                --- PREGUNTA ---
                [id: protocolo]
                Pregunta: ¿Qué es Ethereum?
                A) Una app
                B) Un protocolo
                Correcta: B
                Explicación: Ethereum se estudia como protocolo.
                --- FIN ---
                """.trimIndent(),
            )

        assertThat(result.report.canImport).isTrue()
        assertThat(result.draft.course.key).isEqualTo("eth")
        assertThat(result.draft.units.single().key).isEqualTo("base")
        assertThat(result.draft.units.single().questions).hasSize(1)
        assertThat(result.draft.units.single().questions.single().key).isEqualTo("protocolo")
        assertThat(result.draft.units.single().questions.single().format).isEqualTo(ReviewedQuestionFormat.MULTIPLE_CHOICE)
        assertThat(result.draft.units.single().questions.single().correctAnswer).isEqualTo("Un protocolo")
    }

    @Test
    fun `extractor infers course unit and reveal cards from raw prose`() {
        val result =
            extractor.extract(
                """
                No, el consumo total esta en maximos historicos. Vos seguro te estas refiriendo al consumo de ciertos productos particulares; yo me refiero a TODO el consumo.

                Sobre los colectivos, no se que pretendes decir. La riqueza de un pais se mide por su PBI, no por la frecuencia de colectivos.

                Y que suba el credito es excelente, porque estaba en niveles africanos. La deuda no es mala; en todos los paises del primer mundo sus ciudadanos se endeudan porque eso permite adelantar consumo.
                """.trimIndent(),
            )

        assertThat(result.report.canImport).isTrue()
        assertThat(result.draft.course.title).isNotEmpty()
        assertThat(result.draft.units).hasSize(1)
        assertThat(result.draft.units.single().questions).hasSize(3)
        assertThat(result.draft.units.single().questions.map { it.format }).containsExactly(
            ReviewedQuestionFormat.REVEAL_ANSWER,
            ReviewedQuestionFormat.REVEAL_ANSWER,
            ReviewedQuestionFormat.REVEAL_ANSWER,
        )
        assertThat(result.draft.units.single().questions.first().correctAnswer).contains("consumo total")
        assertThat(result.report.authoringWarnings.map { it.code }).containsAtLeast(
            "course_inferred_from_text",
            "unit_inferred_from_text",
            "questions_generated_from_paragraphs",
            "question_id_generated_from_stem",
        )
    }

    @Test
    fun `extractor does not treat a rhetorical question as a weak structured item`() {
        val result =
            extractor.extract(
                """
                Curso: Ethereum
                Unidad: Base

                ¿Por qué importa la ejecución verificable? Porque permite coordinar estado compartido.
                Esta línea explica la idea, no es una tarjeta.
                """.trimIndent(),
            )

        val question = result.draft.units.single().questions.single()
        assertThat(question.format).isEqualTo(ReviewedQuestionFormat.REVEAL_ANSWER)
        assertThat(question.stem).contains("Punto 1")
        assertThat(result.report.authoringWarnings.map { it.code }).doesNotContain("weak_question_boundary_detected")
    }

    @Test
    fun `extractor accepts weak question only inside evaluable zone with answer evidence`() {
        val result =
            extractor.extract(
                """
                Curso: Ethereum
                Unidad: Base

                Preguntas
                ¿Qué coordina Ethereum?
                Respuesta: cambios de estado compartidos
                """.trimIndent(),
            )

        val question = result.draft.units.single().questions.single()
        assertThat(question.stem).isEqualTo("¿Qué coordina Ethereum?")
        assertThat(question.feedback).isEqualTo("Respuesta correcta: cambios de estado compartidos.")
        assertThat(result.report.authoringWarnings.map { it.code }).contains("weak_question_boundary_detected")
        assertThat(result.report.authoringWarnings.map { it.code }).contains("feedback_generated_mechanical")
    }

    @Test
    fun `question explicit id preserves item identity when stem changes`() {
        val first =
            compiler.compile(
                extractor.extract(
                    sourceWithQuestion(
                        stem = "¿Qué es Ethereum?",
                        id = "ethereum_concepto",
                    ),
                ).draft,
                now = 10L,
            )
        val second =
            compiler.compile(
                extractor.extract(
                    sourceWithQuestion(
                        stem = "¿Cómo debe entenderse Ethereum?",
                        id = "ethereum_concepto",
                    ),
                ).draft,
                now = 10L,
            )

        assertThat(first.items.single().itemId).isEqualTo("eth__base__contenido__ethereum_concepto")
        assertThat(second.items.single().itemId).isEqualTo(first.items.single().itemId)
    }

    @Test
    fun `question without explicit id warns because stem generated identity is fragile`() {
        val result =
            extractor.extract(
                """
                Curso: Ethereum
                ID curso: eth
                Unidad: Base
                ID unidad: base
                Pregunta: ¿Qué es Ethereum?
                Respuesta: Un protocolo.
                Explicación: Se estudia como protocolo.
                """.trimIndent(),
            )

        assertThat(result.draft.units.single().questions.single().key).isEqualTo("que_es_ethereum")
        assertThat(result.report.authoringWarnings.map { it.code }).contains("question_id_generated_from_stem")
    }

    @Test
    fun `choose false statement does not imply trap role`() {
        val draft =
            extractor.extract(
                """
                Curso: Ethereum
                ID curso: eth
                Unidad: Base
                ID unidad: base

                --- PREGUNTA ---
                [id: falsa_normal]
                Pregunta: ¿Cuál afirmación es falsa?
                A) Ethereum coordina estado compartido.
                B) Ethereum es únicamente una aplicación móvil.
                Correcta: B
                Explicación: La opción falsa reduce Ethereum a una aplicación.
                --- FIN ---
                """.trimIndent(),
            ).draft

        val question = draft.units.single().questions.single()
        val contentPackage = compiler.compile(draft, now = 10L)

        assertThat(question.format).isEqualTo(ReviewedQuestionFormat.CHOOSE_FALSE_STATEMENT)
        assertThat(question.studyRole).isEqualTo(ReviewedQuestionStudyRole.NORMAL)
        assertThat(contentPackage.items.single().itemRole).isEqualTo(ItemRole.CORE)
        assertThat(contentPackage.items.single().facet).isEqualTo(FacetType.DEFINICION_FUNCIONAL)
    }

    @Test
    fun `trap role requires explicit confusion text`() {
        val draft =
            extractor.extract(
                """
                Curso: Ethereum
                ID curso: eth
                Unidad: Base
                ID unidad: base

                Pregunta: ¿Qué error detecta?
                A) Protocolo
                B) App
                Correcta: A
                Explicación: Ethereum se estudia como protocolo.
                Rol: Trampa
                """.trimIndent(),
            ).draft

        val report = draftValidator.validate(draft)

        assertThat(report.canImport).isFalse()
        assertThat(report.structuralErrors.map { it.code }).contains("trap_confusion_missing:que_error_detecta")
    }

    @Test
    fun `compiler creates one node per unit and never rescue role`() {
        val contentPackage = compiler.compile(validDraft(), now = 10L)

        assertThat(contentPackage.packageId).isEqualTo("editable_eth")
        assertThat(contentPackage.courses.single().courseId).isEqualTo("eth")
        assertThat(contentPackage.units.map { it.unitId }).containsExactly("eth__base", "eth__avanzado").inOrder()
        assertThat(contentPackage.nodes.map { it.nodeId }).containsExactly("eth__base__contenido", "eth__avanzado__contenido").inOrder()
        assertThat(contentPackage.nodes.first().facets).contains(FacetType.ERROR_TIPICO)
        assertThat(contentPackage.nodes.first().mustKnow.single()).contains("q1:")
        assertThat(contentPackage.nodes.first().commonErrors).containsExactly("Confundir Ethereum con una app.")
        assertThat(contentPackage.items.map { it.itemRole }).doesNotContain(ItemRole.RESCUE)
        assertThat(contentPackage.items.map { it.itemId }).containsAtLeast(
            "eth__base__contenido__q1",
            "eth__avanzado__contenido__q2",
        )
    }

    @Test
    fun `reviewed bank profile accepts complete editable package without node common errors`() = runTest {
        val contentPackage =
            compiler.compile(
                validDraft(
                    units =
                        listOf(
                            ReviewedEditableUnitDraft(
                                title = "Base",
                                key = "base",
                                sourceRef = "apunte",
                                questions =
                                    listOf(
                                        ReviewedEditableQuestionDraft(
                                            key = "q1",
                                            stem = "¿Qué es Ethereum?",
                                            format = ReviewedQuestionFormat.REVEAL_ANSWER,
                                            studyRole = ReviewedQuestionStudyRole.NORMAL,
                                            correctAnswer = "Un protocolo.",
                                            feedback = "Se estudia como protocolo.",
                                        ),
                                    ),
                            ),
                        ),
                ),
                now = 10L,
            )

        val defaultReport = importValidator.validate(contentPackage, ImportValidationProfile.PEDAGOGICAL_NODE)
        val reviewedReport = importValidator.validate(contentPackage, ImportValidationProfile.REVIEWED_QUESTION_BANK)

        assertThat(defaultReport.structuralErrors.map { it.code }).contains("node_common_errors_empty:eth__base__contenido")
        assertThat(reviewedReport.canImport).isTrue()
        assertThat(reviewedReport.structuralErrors).isEmpty()
    }

    @Test
    fun `preparation service returns compiled json only after source and editable validation pass`() = runTest {
        val service =
            EditableImportPreparationService(
                draftValidator = draftValidator,
                compiler = compiler,
                importValidator = importValidator,
                timeProvider = FixedEditableTimeProvider(10L),
            )
        val extracted = extractor.extract(sourceWithQuestion())

        val prepared = service.prepare(extracted.draft, extracted.report)

        assertThat(prepared.canImport).isTrue()
        assertThat(prepared.contentPackageJson).contains("\"packageId\":\"editable_eth\"")
        assertThat(prepared.report.structuralErrors).isEmpty()
    }
}

private fun sourceWithQuestion(
    stem: String = "¿Qué es Ethereum?",
    id: String = "ethereum_concepto",
): String =
    """
    Curso: Ethereum
    ID curso: eth
    Unidad: Base
    ID unidad: base
    Fuente: apunte

    --- PREGUNTA ---
    [id: $id]
    Pregunta: $stem
    Respuesta: Un protocolo.
    Explicación: Se estudia como protocolo.
    --- FIN ---
    """.trimIndent()

private fun validDraft(
    units: List<ReviewedEditableUnitDraft> =
        listOf(
            ReviewedEditableUnitDraft(
                title = "Base",
                key = "base",
                sourceRef = "apunte",
                questions =
                    listOf(
                        ReviewedEditableQuestionDraft(
                            key = "q1",
                            stem = "¿Cuál describe mejor Ethereum?",
                            format = ReviewedQuestionFormat.MULTIPLE_CHOICE,
                            studyRole = ReviewedQuestionStudyRole.TRAP,
                            options =
                                listOf(
                                    ReviewedEditableOptionDraft(key = "A", text = "Un protocolo", isCorrect = true),
                                    ReviewedEditableOptionDraft(key = "B", text = "Una app", isCorrect = false),
                                ),
                            correctAnswer = "Un protocolo",
                            feedback = "Ethereum se estudia como protocolo, no como app.",
                            correctedConfusion = "Confundir Ethereum con una app.",
                        ),
                    ),
            ),
            ReviewedEditableUnitDraft(
                title = "Avanzado",
                key = "avanzado",
                sourceRef = "apunte",
                questions =
                    listOf(
                        ReviewedEditableQuestionDraft(
                            key = "q2",
                            stem = "Ethereum coordina cambios de estado compartidos.",
                            format = ReviewedQuestionFormat.TRUE_FALSE,
                            studyRole = ReviewedQuestionStudyRole.NORMAL,
                            correctAnswer = "Verdadero",
                            feedback = "La red verifica cambios de estado bajo reglas compartidas.",
                        ),
                    ),
            ),
        ),
): ReviewedEditableImportDraft =
    ReviewedEditableImportDraft(
        course = ReviewedEditableCourseDraft(title = "Ethereum", key = "eth"),
        units = units,
    )

private class FixedEditableTimeProvider(
    private val now: Long,
) : TimeProvider {
    override fun now(): Long = now
}
