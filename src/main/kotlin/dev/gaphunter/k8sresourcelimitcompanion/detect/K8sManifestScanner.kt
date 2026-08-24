package dev.gaphunter.k8sresourcelimitcompanion.detect

import dev.gaphunter.k8sresourcelimitcompanion.model.ContainerHit
import dev.gaphunter.k8sresourcelimitcompanion.model.ResourceProblem

/**
 * Plain-text line scanner for Kubernetes workload manifests (YAML) --
 * flags any container entry under a `containers:` (or `initContainers:`)
 * list that has no `resources:` block, or has one missing `requests`
 * or `limits`. No requests/limits is a real, documented Kubernetes
 * footgun: the scheduler can't make good placement decisions, and a
 * container with no limit can starve its neighbors on the same node.
 *
 * **Deliberately indentation-based, not a real YAML parser** -- same
 * "plain-text line scan" discipline as `ConfigLineScanner`
 * (config-secrets-file-companion) and `EnvVarReferenceScanner`
 * (env-var-missing-companion): a container's own indentation level
 * defines where its block ends (the first line at or below that
 * indentation that isn't blank/a comment). Handles the common
 * `containers:` / `- name: x` / nested `resources:` shape; multi-doc
 * files (`---` separators) and anchors/aliases aren't specially
 * resolved.
 *
 * **v0.1 scope, stated honestly:** only scans files whose `kind:` is a
 * workload kind that actually runs containers (Deployment, Pod,
 * StatefulSet, DaemonSet, Job, CronJob) -- a raw List/ConfigMap/Service
 * manifest is skipped entirely, and a manifest with no recognized
 * `kind:` at all is skipped (nothing to check).
 */
object K8sManifestScanner {

    private val WORKLOAD_KIND = Regex(
        """^kind:\s*["']?(Deployment|Pod|StatefulSet|DaemonSet|Job|CronJob)["']?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val CONTAINERS_KEY = Regex("""^(\s*)(containers|initContainers):\s*$""")
    private val CONTAINER_NAME_ENTRY = Regex("""^(\s*)-\s*name:\s*["']?([\w.-]+)["']?\s*$""")
    private val RESOURCES_KEY = Regex("""^(\s*)resources:\s*$""")
    private val REQUESTS_KEY = Regex("""^\s*requests:\s*$""")
    private val LIMITS_KEY = Regex("""^\s*limits:\s*$""")

    fun scan(text: String): List<ContainerHit> {
        if (!looksLikeWorkloadManifest(text)) return emptyList()

        val lines = text.lines()
        val hits = mutableListOf<ContainerHit>()

        var i = 0
        while (i < lines.size) {
            val containersMatch = CONTAINERS_KEY.find(lines[i])
            if (containersMatch == null) {
                i++
                continue
            }
            val listIndent = containersMatch.groupValues[1].length
            i++

            // Walk each `- name: x` entry directly under this containers: list.
            while (i < lines.size) {
                val line = lines[i]
                if (isBlockEnd(line, listIndent)) break

                val entryMatch = CONTAINER_NAME_ENTRY.find(line)
                if (entryMatch == null) {
                    i++
                    continue
                }
                val entryIndent = entryMatch.groupValues[1].length
                val name = entryMatch.groupValues[2]
                val bodyStart = i + 1
                var bodyEnd = bodyStart
                while (bodyEnd < lines.size && !isBlockEnd(lines[bodyEnd], entryIndent)) bodyEnd++
                val body = lines.subList(bodyStart, bodyEnd)

                hits += problemsFor(name, body, i + 1)
                i = bodyEnd
            }
        }

        return hits
    }

    private fun problemsFor(name: String, body: List<String>, containerLineNumber: Int): List<ContainerHit> {
        val resourcesLine = body.indexOfFirst { RESOURCES_KEY.matches(it) }
        if (resourcesLine < 0) {
            return listOf(ContainerHit(name, ResourceProblem.NO_RESOURCES_BLOCK, containerLineNumber))
        }

        val resourcesIndent = RESOURCES_KEY.find(body[resourcesLine])!!.groupValues[1].length
        var resourcesEnd = resourcesLine + 1
        while (resourcesEnd < body.size && !isBlockEnd(body[resourcesEnd], resourcesIndent)) resourcesEnd++
        val resourcesBody = body.subList(resourcesLine + 1, resourcesEnd)

        val hits = mutableListOf<ContainerHit>()
        if (resourcesBody.none { REQUESTS_KEY.matches(it) }) {
            hits += ContainerHit(name, ResourceProblem.MISSING_REQUESTS, containerLineNumber)
        }
        if (resourcesBody.none { LIMITS_KEY.matches(it) }) {
            hits += ContainerHit(name, ResourceProblem.MISSING_LIMITS, containerLineNumber)
        }
        return hits
    }

    /** True when [line] is blank/comment (doesn't end a block), or is a real line at/below [indent] (ends it). */
    private fun isBlockEnd(line: String, indent: Int): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return false
        val lineIndent = line.length - line.trimStart().length
        return lineIndent <= indent
    }

    private fun looksLikeWorkloadManifest(text: String): Boolean =
        text.lineSequence().any { WORKLOAD_KIND.matches(it) }
}
