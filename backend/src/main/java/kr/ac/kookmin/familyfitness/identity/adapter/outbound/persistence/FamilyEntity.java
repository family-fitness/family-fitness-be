package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "families")
public class FamilyEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "region_code", length = 20)
    private @Nullable String regionCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FamilyEntity() {}

    public FamilyEntity(UUID id, String name, @Nullable String regionCode, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.regionCode = regionCode;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public @Nullable String getRegionCode() {
        return regionCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
