package kr.ac.kookmin.familyfitness

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModularityTests {
    private val modules = ApplicationModules.of(FamilyfitnessApplication::class.java)

    @Test
    fun `업무 모듈 네 개가 실제로 존재한다`() {
        assertThat(modules.stream().map { it.identifier.toString() }.toList())
            .containsExactlyInAnyOrder("identity", "fitness", "activity", "coaching")
    }

    @Test
    fun `모듈 순환 참조와 내부 패키지 접근 및 선언된 의존 규칙을 검증한다`() {
        modules.verify()
    }

    @Test
    fun `identity는 독립적이고, fitness·activity는 identity만, coaching은 identity·fitness·activity를 참조한다`() {
        val basePackage = "kr.ac.kookmin.familyfitness"
        val classes =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages(basePackage)
        val allowed =
            mapOf(
                "identity" to emptySet(),
                "fitness" to setOf("identity"),
                "activity" to setOf("identity"),
                "coaching" to setOf("identity", "fitness", "activity"),
            )

        allowed.forEach { (source, targets) ->
            val forbidden = (allowed.keys - source - targets).map { "$basePackage.$it.." }.toTypedArray()
            noClasses()
                .that()
                .resideInAPackage("$basePackage.$source..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(*forbidden)
                .allowEmptyShould(true)
                .check(classes)
        }
    }
}
