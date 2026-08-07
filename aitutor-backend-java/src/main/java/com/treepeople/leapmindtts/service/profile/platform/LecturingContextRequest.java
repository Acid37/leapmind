package com.treepeople.leapmindtts.service.profile.platform;
import java.util.List;
public record LecturingContextRequest(KnowledgePointRef knowledgePoint) { public LecturingContextRequest { if (!(knowledgePoint instanceof KnowledgePointRef.None) && !(knowledgePoint instanceof KnowledgePointRef.Resolved)) throw new IllegalArgumentException("knowledgePoint must be none or resolved"); } }
