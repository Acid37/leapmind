package com.treepeople.leapmindtts.service.profile.platform;
public record ExplainingContextRequest(KnowledgePointRef.Resolved knowledgePoint) { public ExplainingContextRequest { if (knowledgePoint == null) throw new IllegalArgumentException("resolved knowledgePoint is required"); } }
