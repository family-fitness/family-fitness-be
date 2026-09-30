package kr.ac.kookmin.familyfitness.coaching.support;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery;
import kr.ac.kookmin.familyfitness.fitness.api.LatestFitness;
import org.jspecify.annotations.Nullable;

public class FakeFitness implements FitnessQuery {
    public final Map<UUID, LatestFitness> latest = new LinkedHashMap<>();

    public record Item(String code, double value) {}

    public void measured(UUID profileId, Item... items) {
        measured(profileId, null, null, items);
    }

    public void measured(
            UUID profileId, @Nullable FactorPoint weakest, @Nullable FactorPoint strongest, Item... items) {
        Map<String, BigDecimal> measurements = new LinkedHashMap<>();
        for (Item item : items) {
            measurements.put(item.code(), BigDecimal.valueOf(item.value()));
        }
        latest.put(
                profileId,
                new LatestFitness(
                        profileId,
                        UUID.randomUUID(),
                        Fixed.TODAY.minusDays(3),
                        new BigDecimal("140.5"),
                        new BigDecimal("35.0"),
                        null,
                        null,
                        measurements,
                        weakest,
                        strongest));
    }

    @Override
    public @Nullable LatestFitness latestOf(UUID profileId) {
        return latest.get(profileId);
    }

    @Override
    public boolean hasAnyTest(java.util.Collection<UUID> profileIds) {
        return profileIds.stream().anyMatch(latest::containsKey);
    }
}
