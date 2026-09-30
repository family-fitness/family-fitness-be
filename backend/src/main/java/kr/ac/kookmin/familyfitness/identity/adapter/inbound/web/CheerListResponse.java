package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.util.List;
import kr.ac.kookmin.familyfitness.identity.api.CheerView;

/** 받은 응원 목록. 최근 것부터. */
public record CheerListResponse(List<CheerView> cheers) {}
