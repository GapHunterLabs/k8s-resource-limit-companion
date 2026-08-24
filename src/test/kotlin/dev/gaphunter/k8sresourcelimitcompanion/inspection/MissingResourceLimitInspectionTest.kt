package dev.gaphunter.k8sresourcelimitcompanion.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MissingResourceLimitInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(MissingResourceLimitInspection::class.java)
    }

    fun `test a container with no resources block produces a warning`() {
        myFixture.configureByText(
            "deployment.yaml",
            """
            kind: Deployment
            spec:
              template:
                spec:
                  containers:
                    - name: web
                      image: acmecorp/web:1.0
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("resources:") == true })
    }

    fun `test a container with requests and limits produces no warning`() {
        myFixture.configureByText(
            "deployment.yaml",
            """
            kind: Deployment
            spec:
              template:
                spec:
                  containers:
                    - name: web
                      resources:
                        requests:
                          cpu: "250m"
                        limits:
                          cpu: "500m"
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("web") == true })
    }

    fun `test a non-yaml file is never scanned`() {
        myFixture.configureByText(
            "Notes.java",
            "String x = \"kind: Deployment\\ncontainers:\\n  - name: web\";",
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("resources:") == true })
    }
}
