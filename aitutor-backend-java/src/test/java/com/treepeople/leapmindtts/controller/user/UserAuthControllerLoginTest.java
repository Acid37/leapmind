package com.treepeople.leapmindtts.controller.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.treepeople.leapmindtts.exception.InvalidCredentialsException;
import com.treepeople.leapmindtts.pojo.dto.UserLoginRequest;
import com.treepeople.leapmindtts.service.user.SmsVerificationCodeService;
import com.treepeople.leapmindtts.service.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserAuthControllerLoginTest {

    @Test
    void invalidCredentialsUseHttp401AndMatchingEnvelopeCode() throws Exception {
        UserService users = mock(UserService.class);
        when(users.login(any(UserLoginRequest.class))).thenThrow(new InvalidCredentialsException("密码错误"));
        UserAuthController controller = new UserAuthController(users, mock(SmsVerificationCodeService.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()))
                .build();

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"demo\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void unexpectedLoginFailureIsSanitizedAsHttp500() throws Exception {
        UserService users = mock(UserService.class);
        when(users.login(any(UserLoginRequest.class))).thenThrow(new RuntimeException("database detail"));
        UserAuthController controller = new UserAuthController(users, mock(SmsVerificationCodeService.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()))
                .build();

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"secret\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("登录服务暂不可用"));
    }
}
