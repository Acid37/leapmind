package com.treepeople.leapmindtts.service.profile.platform;
public record ConversationContextRequest(KnowledgePointRef knowledgePoint) { public ConversationContextRequest { if (!(knowledgePoint instanceof KnowledgePointRef.None) && !(knowledgePoint instanceof KnowledgePointRef.Resolved)) throw new IllegalArgumentException("knowledgePoint must be none or resolved"); } }
