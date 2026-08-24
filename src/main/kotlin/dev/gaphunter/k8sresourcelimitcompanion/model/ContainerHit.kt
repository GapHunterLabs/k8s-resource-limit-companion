package dev.gaphunter.k8sresourcelimitcompanion.model

enum class ResourceProblem {
    /** No `resources:` block at all under this container. */
    NO_RESOURCES_BLOCK,

    /** Has `resources:` but no `requests` sub-key. */
    MISSING_REQUESTS,

    /** Has `resources:` but no `limits` sub-key. */
    MISSING_LIMITS,
}

/** One container entry (by name, or its list index if unnamed) with a resource-declaration problem. */
data class ContainerHit(
    val containerLabel: String,
    val problem: ResourceProblem,
    val lineNumber: Int,
)
