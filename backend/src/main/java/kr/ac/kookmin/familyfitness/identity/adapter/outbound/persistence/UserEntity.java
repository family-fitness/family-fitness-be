package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/*
 * JPA 엔티티는 V1__init.sql 의 컬럼을 그대로 따른다(ddl-auto=validate). 연관관계 대신 ID 컬럼을 들고,
 * 애그리게잇 조립은 FamilyRepositoryAdapter 가 한다. 열거형은 varchar 로 저장하고 매퍼가 변환한다.
 */
@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 191)
    private String providerUserId;

    @Column(name = "email", length = 255)
    private @Nullable String email;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 처음 가입할 때 실려 온 utm 값(V174). 없으면 null. */
    @Column(name = "signup_utm_source", length = 64)
    private @Nullable String signupUtmSource;

    @Column(name = "signup_utm_medium", length = 64)
    private @Nullable String signupUtmMedium;

    @Column(name = "signup_utm_campaign", length = 64)
    private @Nullable String signupUtmCampaign;

    protected UserEntity() {}

    public UserEntity(
            UUID id,
            String provider,
            String providerUserId,
            @Nullable String email,
            String status,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public @Nullable String getEmail() {
        return email;
    }

    public void setEmail(@Nullable String email) {
        this.email = email;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public @Nullable String getSignupUtmSource() {
        return signupUtmSource;
    }

    public @Nullable String getSignupUtmMedium() {
        return signupUtmMedium;
    }

    public @Nullable String getSignupUtmCampaign() {
        return signupUtmCampaign;
    }

    /** 세 칸 가운데 하나라도 차 있으면 이미 유입 경로가 있는 계정이다. */
    public boolean hasSignupSource() {
        return signupUtmSource != null || signupUtmMedium != null || signupUtmCampaign != null;
    }

    public void setSignupSource(@Nullable String source, @Nullable String medium, @Nullable String campaign) {
        this.signupUtmSource = source;
        this.signupUtmMedium = medium;
        this.signupUtmCampaign = campaign;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
