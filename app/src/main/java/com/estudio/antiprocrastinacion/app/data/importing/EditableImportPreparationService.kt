package com.estudio.antiprocrastinacion.app.data.importing

import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidator
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.model.authoring.ReviewedEditableImportDraft
import com.estudio.antiprocrastinacion.app.model.json.ContentPackageDto
import com.estudio.antiprocrastinacion.app.ui.common.AppJson
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString

data class EditableImportPreparationResult(
    val sourceReport: ValidationReport,
    val draftReport: ValidationReport,
    val finalReport: ValidationReport,
    val contentPackage: ContentPackageDto?,
    val contentPackageJson: String?,
) {
    val report: ValidationReport = sourceReport + draftReport + finalReport
    val canImport: Boolean = contentPackage != null && report.canImport
}

@Singleton
class EditableImportPreparationService @Inject constructor(
    private val draftValidator: ReviewedEditableDraftValidator,
    private val compiler: ReviewedQuestionBankCompiler,
    private val importValidator: ImportValidator,
    private val timeProvider: TimeProvider,
) {
    suspend fun prepare(
        draft: ReviewedEditableImportDraft,
        sourceReport: ValidationReport = ValidationReport(emptyList(), emptyList()),
        compilationContext: ReviewedQuestionBankCompilationContext = ReviewedQuestionBankCompilationContext(),
    ): EditableImportPreparationResult {
        val draftReport = draftValidator.validate(draft)
        if (!(sourceReport + draftReport).canImport) {
            return EditableImportPreparationResult(
                sourceReport = sourceReport,
                draftReport = draftReport,
                finalReport = ValidationReport(emptyList(), emptyList()),
                contentPackage = null,
                contentPackageJson = null,
            )
        }
        val contentPackage = compiler.compile(draft, timeProvider.now(), compilationContext)
        val finalReport =
            importValidator.validate(
                contentPackage = contentPackage,
                profile = ImportValidationProfile.REVIEWED_QUESTION_BANK,
        )
        return EditableImportPreparationResult(
            sourceReport = sourceReport,
            draftReport = draftReport,
            finalReport = finalReport,
            contentPackage = contentPackage,
            contentPackageJson = AppJson.encodeToString(contentPackage),
        )
    }
}

private operator fun ValidationReport.plus(other: ValidationReport): ValidationReport =
    ValidationReport(
        structuralErrors = structuralErrors + other.structuralErrors,
        authoringWarnings = authoringWarnings + other.authoringWarnings,
    )
