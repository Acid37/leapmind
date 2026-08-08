package com.treepeople.leapmindtts.service.profile.security;

import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.mapper.M6ProfileActorMapper;
import com.treepeople.leapmindtts.pojo.entity.M6ProfileActor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileActorResolver {
    private final M6ProfileActorMapper users;

    public Long requireActor(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object jwtUserId = request.getAttribute("userId");
        Object jwtUsername = request.getAttribute("username");
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(jwtUserId instanceof Long userId) || userId <= 0
                || !(jwtUsername instanceof String username) || !username.equals(authentication.getName())) {
            throw unauthenticated();
        }
        M6ProfileActor user = users.selectActorById(userId);
        if (user == null || !username.equals(user.getUsername())) throw unauthenticated();
        if (!Integer.valueOf(1).equals(user.getStatus())) throw denied();
        return userId;
    }

    public void authorizeSelf(HttpServletRequest request, Long targetUserId) {
        if (!requireActor(request).equals(targetUserId)) throw denied();
    }

    private M6ApiException unauthenticated() {
        return new M6ApiException(HttpStatus.UNAUTHORIZED, "PROFILE_UNAUTHENTICATED", "未认证");
    }
    private M6ApiException denied() {
        return new M6ApiException(HttpStatus.FORBIDDEN, "PROFILE_ACCESS_DENIED", "无权访问");
    }
}
