package dev.gaphunter.k8sresourcelimitcompanion.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.gaphunter.k8sresourcelimitcompanion.detect.K8sManifestScanner
import dev.gaphunter.k8sresourcelimitcompanion.model.ContainerHit
import dev.gaphunter.k8sresourcelimitcompanion.model.ResourceProblem
import dev.gaphunter.k8sresourcelimitcompanion.review.ReviewPrompt

/**
 * Flags a Kubernetes workload manifest's container entry that has no
 * `resources:` block, or one missing `requests`/`limits` -- the
 * scheduler can't make good placement decisions without requests, and
 * a container with no limit can starve its neighbors on the same node.
 *
 * Runs via [checkFile] (whole-file text scan), same reasoning as
 * `config-secrets-file-companion`'s `HardcodedConfigSecretInspection`:
 * detection is plain-text line scanning against indentation, not a PSI
 * walk of a specific grammar -- see `build.gradle.kts` for why no YAML
 * PSI dependency is taken.
 */
class MissingResourceLimitInspection : LocalInspectionTool() {

    companion object {
        const val MAX_FILE_LENGTH = 500_000
        private val YAML_FILE_NAME = Regex("""^[^.]+\.ya?ml$""", RegexOption.IGNORE_CASE)
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        val virtualFile = file.virtualFile ?: return null
        if (!YAML_FILE_NAME.matches(virtualFile.name)) return null

        val text = file.text
        if (text.length > MAX_FILE_LENGTH) return null

        val hits = K8sManifestScanner.scan(text)
        if (hits.isEmpty()) return null

        val document = file.viewProvider.document ?: return null
        val problems = mutableListOf<ProblemDescriptor>()

        for (hit in hits) {
            if (hit.lineNumber - 1 !in 0 until document.lineCount) continue
            val lineStartOffset = document.getLineStartOffset(hit.lineNumber - 1)
            val lineEndOffset = document.getLineEndOffset(hit.lineNumber - 1)
            val anchor = leafElementAt(file, lineStartOffset) ?: continue
            val anchorStart = anchor.textRange.startOffset
            val relativeRange = TextRange(
                (lineStartOffset - anchorStart).coerceAtLeast(0),
                (lineEndOffset - anchorStart).coerceAtMost(anchor.textLength),
            )
            if (relativeRange.startOffset >= relativeRange.endOffset) continue

            problems += manager.createProblemDescriptor(
                anchor,
                relativeRange,
                messageFor(hit),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                isOnTheFly,
            )

            ReviewPrompt.recordHit(file.project, "${virtualFile.path}:${hit.lineNumber}:${hit.problem}")
        }

        return if (problems.isEmpty()) null else problems.toTypedArray()
    }

    private fun messageFor(hit: ContainerHit): String = when (hit.problem) {
        ResourceProblem.NO_RESOURCES_BLOCK -> "Container '${hit.containerLabel}' has no resources: block -- the scheduler can't make good placement decisions, and it can consume unlimited resources on its node"
        ResourceProblem.MISSING_REQUESTS -> "Container '${hit.containerLabel}' has resources: but no requests -- the scheduler can't reserve capacity for it"
        ResourceProblem.MISSING_LIMITS -> "Container '${hit.containerLabel}' has resources: but no limits -- it can consume unlimited resources and starve its neighbors on the same node"
    }

    private fun leafElementAt(file: PsiFile, startOffset: Int): PsiElement? {
        if (startOffset < 0 || startOffset >= file.textLength) return null
        var element = file.findElementAt(startOffset) ?: return file
        while (element.firstChild != null) {
            element = element.firstChild
        }
        return element
    }
}
