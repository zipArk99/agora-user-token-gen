package com.example.agora.authentication;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.agora.chat.ChatTokenBuilder2;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/auth")
@CrossOrigin
public class AgoraChatTokenController {

    @Value("${appid}")
    private String appid;

    @Value("${appcertificate}")
    private String appcert;

    @Value("${expire.second}")
    private int expire;

    @Value("${appkey}")
    private String appkey;

    @Value("${domain}")
    private String domain;

    private final RestTemplate restTemplate = new RestTemplate();

    private Cache<String, String> agoraChatAppTokenCache;

    @PostConstruct
    public void init() {
        agoraChatAppTokenCache = CacheBuilder.newBuilder().maximumSize(1).expireAfterWrite(expire, TimeUnit.SECONDS).build();
    }


    @GetMapping("/chat/app/token")
    public String getAppToken() {

        if (!StringUtils.hasText(appid) || !StringUtils.hasText(appcert)) {
            return "appid or appcert is not empty";
        }

        return getAgoraAppToken();
    }

    /**
     * Gets a token with user privileges.
     *
     * @param chatUserName ChatUserName
     * @return user privileges token
     */
    @GetMapping("/chat/user/{chatUserName}/token")
    public String getChatToken(@PathVariable String chatUserName) {

        if (!StringUtils.hasText(appid) || !StringUtils.hasText(appcert)) {
            return "appid or appcert is not empty";
        }

        if (!StringUtils.hasText(appkey) || !StringUtils.hasText(domain)) {
            return "appkey or domain is not empty";
        }

        if (!appkey.contains("#")) {
            return "appkey is illegal";
        }

        if (!StringUtils.hasText(chatUserName)) {
            return "chatUserName is not empty";
        }

        ChatTokenBuilder2 builder = new ChatTokenBuilder2();

        String chatUserUuid = getChatUserUuid(chatUserName);

        if (chatUserUuid == null) {
            chatUserUuid = registerChatUser(chatUserName);
        }

        return builder.buildUserToken(appid, appcert, chatUserUuid, expire);
    }

    /**
     * Creates a user with the password "123", and gets UUID.
     *
     * @param chatUserName The username of the agora chat user.
     * @return uuid
     */
    private String registerChatUser(String chatUserName) {

        String orgName = appkey.split("#")[0];

        String appName = appkey.split("#")[1];

        String url = "http://" + domain + "/" + orgName + "/" + appName + "/users";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(getAgoraChatAppTokenFromCache());

        Map<String, String> body = new HashMap<>();
        body.put("username", chatUserName);
        body.put("password", "123");

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response;

        try {
            response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        } catch (Exception e) {
            throw new RestClientException("register chat user error : " + e.getMessage());
        }

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("entities");

        return (String) results.get(0).get("uuid");
    }

    /**
     * Gets the UUID of the user.
     *
     * @param chatUserName The username of the agora chat user.
     * @return uuid
     */
    private @Nullable String getChatUserUuid(String chatUserName) {

        String orgName = appkey.split("#")[0];

        String appName = appkey.split("#")[1];

        String url = "http://" + domain + "/" + orgName + "/" + appName + "/users/" + chatUserName;

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(getAgoraChatAppTokenFromCache());

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(null, headers);

        ResponseEntity<Map> responseEntity = null;

        try {
            responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        } catch (Exception e) {
            System.out.println("get chat user error : " + e.getMessage());
        }

        if (responseEntity != null) {

            List<Map<String, Object>> results = (List<Map<String, Object>>) responseEntity.getBody().get("entities");

            return (String) results.get(0).get("uuid");
        }

        return null;
    }

    /**
     * Generate a token with app privileges.
     *
     * @return token
     */
    private String getAgoraAppToken() {
        if (!StringUtils.hasText(appid) || !StringUtils.hasText(appcert)) {
            throw new IllegalArgumentException("appid or appcert is not empty");
        }

        // Use agora App Id and App Cert to generate an Agora app token.
        ChatTokenBuilder2 builder = new ChatTokenBuilder2();
        return builder.buildAppToken(appid, appcert, expire);
    }

    /**
     * Get the token with app privileges from the cache
     *
     * @return token
     */
    private String getAgoraChatAppTokenFromCache() {
        try {
            return agoraChatAppTokenCache.get("agora-chat-app-token", () -> {
                return getAgoraAppToken();
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Get Agora Chat app token from cache error");
        }
    }

}
