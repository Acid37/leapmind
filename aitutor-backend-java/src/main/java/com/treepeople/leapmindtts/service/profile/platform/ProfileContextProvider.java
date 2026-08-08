package com.treepeople.leapmindtts.service.profile.platform;

public interface ProfileContextProvider {
    PracticeProfileContext practice(ProfileAccessContext access, PracticeContextRequest request);
    ExplainingProfileContext explaining(ProfileAccessContext access, ExplainingContextRequest request);
    LecturingProfileContext lecturing(ProfileAccessContext access, LecturingContextRequest request);
    ConversationProfileContext conversation(ProfileAccessContext access, ConversationContextRequest request);
    LessonPrepProfileContext lessonPrep(ProfileAccessContext access, LessonPrepContextRequest request);
    FullProfileContext getFullProfile(ProfileAccessContext access);
    KnowledgeStatusContext getKnowledgeStatus(ProfileAccessContext access, KnowledgeStatusRequest request);
}
