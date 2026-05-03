package com.moba.business.controller;

import com.moba.business.entity.User;
import com.moba.business.repository.UserRepository;
import com.moba.common.protocol.ApiResponse;
import com.moba.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${jwt.secret:moba-game-jwt-secret-key-2024-must-be-at-least-256-bits}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @PostMapping("/login")
    public ApiResponse login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        if (username == null || password == null) {
            return ApiResponse.error(400, "用户名和密码不能为空");
        }
        var userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ApiResponse.error(401, "用户不存在");
        }
        User user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return ApiResponse.error(401, "密码错误");
        }
        user.setState("ONLINE");
        userRepository.save(user);
        String token = JwtUtil.generateToken(user.getId(), user.getUsername(), jwtSecret, jwtExpiration);
        log.info("用户登录: {} (id={})", username, user.getId());
        return ApiResponse.success(Map.of(
                "token", token,
                "userId", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "rank", user.getLevel(),
                "rankScore", user.getRankScore()
        ));
    }

    @PostMapping("/register")
    public ApiResponse register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String nickname = request.get("nickname");
        if (username == null || password == null || nickname == null) {
            return ApiResponse.error(400, "用户名、密码和昵称不能为空");
        }
        if (userRepository.existsByUsername(username)) {
            return ApiResponse.error(400, "用户名已存在");
        }
        if (userRepository.existsByNickname(nickname)) {
            return ApiResponse.error(400, "昵称已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setLevel(1);
        user.setRankScore(1000);
        user.setTotalBattles(0);
        user.setWinCount(0);
        user.setLoseCount(0);
        user.setState("ONLINE");
        User saved = userRepository.save(user);
        String token = JwtUtil.generateToken(saved.getId(), saved.getUsername(), jwtSecret, jwtExpiration);
        log.info("用户注册: {} (id={})", username, saved.getId());
        return ApiResponse.success(Map.of(
                "token", token,
                "userId", saved.getId(),
                "username", saved.getUsername(),
                "nickname", saved.getNickname(),
                "rank", saved.getLevel(),
                "rankScore", saved.getRankScore()
        ));
    }

    @PostMapping("/refresh")
    public ApiResponse refresh(@RequestHeader("X-User-Id") String userId,
                               @RequestHeader("X-Username") String username) {
        String token = JwtUtil.generateToken(Long.parseLong(userId), username, jwtSecret, jwtExpiration);
        return ApiResponse.success(Map.of("token", token));
    }
}
