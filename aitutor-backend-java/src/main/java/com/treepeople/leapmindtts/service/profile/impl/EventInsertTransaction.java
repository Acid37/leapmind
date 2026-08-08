package com.treepeople.leapmindtts.service.profile.impl;

import com.treepeople.leapmindtts.mapper.UserEventMapper;
import com.treepeople.leapmindtts.pojo.entity.UserEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventInsertTransaction {
    private final UserEventMapper mapper;
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(UserEvent event) { mapper.insert(event); }
}
