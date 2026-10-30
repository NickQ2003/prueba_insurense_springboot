package com.example.policies.adapters.out.persistence.entity;

import com.example.policies.domain.model.PartyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parties")
public class PartyJpaEntity {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "party_type", nullable = false, length = 20)
  private PartyType partyType;

  @Column(name = "display_name", nullable = false, length = 200)
  private String displayName;

  @Column(name = "document_reference", nullable = false, length = 100, unique = true)
  private String documentReference;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PartyJpaEntity() {}
}
