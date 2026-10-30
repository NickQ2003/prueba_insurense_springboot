package com.example.policies.application.usecase;

import com.example.policies.domain.model.Policy;
import com.example.policies.domain.model.Risk;
import java.util.List;

public record PolicyWithRisks(Policy policy, List<Risk> risks) {}
