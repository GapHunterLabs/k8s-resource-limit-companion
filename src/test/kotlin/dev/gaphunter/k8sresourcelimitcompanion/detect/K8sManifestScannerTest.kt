package dev.gaphunter.k8sresourcelimitcompanion.detect

import dev.gaphunter.k8sresourcelimitcompanion.model.ResourceProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class K8sManifestScannerTest {

    @Test
    fun `container with no resources block is flagged`() {
        val text = """
            kind: Deployment
            spec:
              template:
                spec:
                  containers:
                    - name: web
                      image: acmecorp/web:1.0
        """.trimIndent()
        val hits = K8sManifestScanner.scan(text)
        assertEquals(1, hits.size)
        assertEquals(ResourceProblem.NO_RESOURCES_BLOCK, hits[0].problem)
        assertEquals("web", hits[0].containerLabel)
    }

    @Test
    fun `container with requests and limits is not flagged`() {
        val text = """
            kind: Deployment
            spec:
              template:
                spec:
                  containers:
                    - name: web
                      image: acmecorp/web:1.0
                      resources:
                        requests:
                          cpu: "250m"
                          memory: "256Mi"
                        limits:
                          cpu: "500m"
                          memory: "512Mi"
        """.trimIndent()
        assertTrue(K8sManifestScanner.scan(text).isEmpty())
    }

    @Test
    fun `container with requests but no limits is flagged once`() {
        val text = """
            kind: Deployment
            spec:
              template:
                spec:
                  containers:
                    - name: web
                      resources:
                        requests:
                          cpu: "250m"
        """.trimIndent()
        val hits = K8sManifestScanner.scan(text)
        assertEquals(1, hits.size)
        assertEquals(ResourceProblem.MISSING_LIMITS, hits[0].problem)
    }

    @Test
    fun `non-workload kind is never scanned`() {
        val text = """
            kind: ConfigMap
            data:
              containers: "not a real k8s container list"
        """.trimIndent()
        assertTrue(K8sManifestScanner.scan(text).isEmpty())
    }

    @Test
    fun `multiple containers are each checked independently`() {
        val text = """
            kind: Pod
            spec:
              containers:
                - name: web
                  resources:
                    requests:
                      cpu: "250m"
                    limits:
                      cpu: "500m"
                - name: sidecar
                  image: acmecorp/sidecar:1.0
        """.trimIndent()
        val hits = K8sManifestScanner.scan(text)
        assertEquals(1, hits.size)
        assertEquals("sidecar", hits[0].containerLabel)
        assertEquals(ResourceProblem.NO_RESOURCES_BLOCK, hits[0].problem)
    }
}
