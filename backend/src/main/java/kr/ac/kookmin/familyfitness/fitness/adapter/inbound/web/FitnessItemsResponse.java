package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessItem;
import kr.ac.kookmin.familyfitness.fitness.domain.InputGroup;
import kr.ac.kookmin.familyfitness.fitness.domain.ValueRange;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

// ---- GET /fitness/items ----
public record FitnessItemsResponse(AgeGroup ageGroup, List<Item> items) {
    public record Item(
            String itemCode,
            String itemName,
            String itemLabel,
            String unit,
            FitnessFactor factor,
            boolean higherIsBetter,
            InputGroup inputGroup,
            boolean optional,
            @Nullable String equipment,
            ValueRange range) {}

    public static FitnessItemsResponse of(AgeGroup ageGroup) {
        return new FitnessItemsResponse(
                ageGroup,
                FitnessItem.forAgeGroup(ageGroup).stream()
                        .map(it -> new Item(
                                it.getCode(),
                                it.getItemName(),
                                it.label(ageGroup),
                                it.getUnit(),
                                it.getFactor(),
                                it.isHigherIsBetter(),
                                it.getInputGroup(),
                                it.isOptional(),
                                it.getEquipment(),
                                it.getRange()))
                        .toList());
    }
}
