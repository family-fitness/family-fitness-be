package kr.ac.kookmin.familyfitness.coaching.domain;

import org.jspecify.annotations.Nullable;

public record ProposalVideo(String videoId, @Nullable Integer startSec) {}
