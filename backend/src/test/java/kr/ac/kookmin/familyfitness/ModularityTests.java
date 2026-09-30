package kr.ac.kookmin.familyfitness;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {
    private final ApplicationModules modules = ApplicationModules.of(FamilyfitnessApplication.class);

    @Test
    @DisplayName("업무 모듈 일곱 개가 실제로 존재한다")
    void 업무_모듈_일곱_개가_실제로_존재한다() {
        assertThat(modules.stream().map(it -> it.getIdentifier().toString()).toList())
                .containsExactlyInAnyOrder(
                        "identity", "fitness", "activity", "progress", "coaching", "league", "notification");
    }

    @Test
    @DisplayName("모듈 순환 참조와 내부 패키지 접근 및 선언된 의존 규칙을 검증한다")
    void 모듈_순환_참조와_내부_패키지_접근_및_선언된_의존_규칙을_검증한다() {
        modules.verify();
    }

    @Test
    @DisplayName(
            "identity는 독립적이고, fitness·activity는 identity만, progress는 identity·activity·fitness, coaching은 progress까지, league는 identity·activity·progress, notification은 identity·coaching·progress·fitness·activity를 참조하고 아무도 notification을 참조하지 않는다")
    void 모듈별_허용된_의존만_참조한다() {
        String basePackage = "kr.ac.kookmin.familyfitness";
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(basePackage);
        Map<String, Set<String>> allowed = new LinkedHashMap<>();
        allowed.put("identity", Set.of());
        allowed.put("fitness", Set.of("identity"));
        allowed.put("activity", Set.of("identity"));
        allowed.put("progress", Set.of("identity", "activity", "fitness"));
        allowed.put("coaching", Set.of("identity", "fitness", "activity", "progress"));
        allowed.put("league", Set.of("identity", "activity", "progress"));
        allowed.put("notification", Set.of("identity", "coaching", "progress", "fitness", "activity"));

        allowed.forEach((source, targets) -> {
            Set<String> others = new LinkedHashSet<>(allowed.keySet());
            others.remove(source);
            others.removeAll(targets);
            String[] forbidden =
                    others.stream().map(it -> basePackage + "." + it + "..").toArray(String[]::new);
            noClasses()
                    .that()
                    .resideInAPackage(basePackage + "." + source + "..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(forbidden)
                    .allowEmptyShould(true)
                    .check(classes);
        });
    }
}
