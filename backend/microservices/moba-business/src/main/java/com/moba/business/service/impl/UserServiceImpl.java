package com.moba.business.service.impl;

import com.moba.business.entity.User;
import com.moba.business.repository.UserRepository;
import com.moba.common.dto.UserDTO;
import com.moba.common.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@DubboService
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final String USER_CACHE_PREFIX = "user:dto:";
    private static final long CACHE_EXPIRE_SECONDS = 3600;

    @Override
    public Optional<UserDTO> findById(Long id) {
        String cacheKey = USER_CACHE_PREFIX + id;
        UserDTO cached = (UserDTO) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<User> user = userRepository.findById(id);
        UserDTO dto = user.map(this::toDTO).orElse(null);
        if (dto != null) {
            redisTemplate.opsForValue().set(cacheKey, dto, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        }
        return Optional.ofNullable(dto);
    }

    @Override
    public Optional<UserDTO> findByUsername(String username) {
        return userRepository.findByUsername(username).map(this::toDTO);
    }

    @Override
    public UserDTO login(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        User user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid password");
        }
        user.setState("ONLINE");
        userRepository.save(user);
        redisTemplate.delete(USER_CACHE_PREFIX + user.getId());
        log.info("User logged in: {} (id={})", username, user.getId());
        return toDTO(user);
    }

    @Override
    @Transactional
    public UserDTO createUser(String username, String password, String nickname) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new IllegalArgumentException("Nickname already exists");
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
        user.setState("OFFLINE");

        User saved = userRepository.save(user);
        log.info("Created new user: {} (id={})", username, saved.getId());
        return toDTO(saved);
    }

    @Override
    @Transactional
    public void updateUserState(Long userId, String state) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setState(state);
            userRepository.save(user);
            redisTemplate.delete(USER_CACHE_PREFIX + userId);
        });
    }

    @Override
    @Transactional
    public void updateUserStats(Long userId, int winCount, int loseCount, int rankScoreDelta) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setWinCount(user.getWinCount() + winCount);
            user.setLoseCount(user.getLoseCount() + loseCount);
            user.setTotalBattles(user.getTotalBattles() + winCount + loseCount);
            user.setRankScore(Math.max(0, user.getRankScore() + rankScoreDelta));
            userRepository.save(user);
            redisTemplate.delete(USER_CACHE_PREFIX + userId);
        });
    }

    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setLevel(user.getLevel());
        dto.setRankScore(user.getRankScore());
        dto.setTotalBattles(user.getTotalBattles());
        dto.setWinCount(user.getWinCount());
        dto.setLoseCount(user.getLoseCount());
        dto.setState(user.getState());
        return dto;
    }
}
