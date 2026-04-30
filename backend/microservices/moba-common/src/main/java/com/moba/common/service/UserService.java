package com.moba.common.service;

import com.moba.common.dto.UserDTO;

import java.util.Optional;

public interface UserService {

    Optional<UserDTO> findById(Long id);

    Optional<UserDTO> findByUsername(String username);

    UserDTO createUser(String username, String password, String nickname);

    UserDTO login(String username, String password);

    void updateUserState(Long userId, String state);

    void updateUserStats(Long userId, int winCount, int loseCount, int rankScoreDelta);
}
