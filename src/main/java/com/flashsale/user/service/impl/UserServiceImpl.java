package com.flashsale.user.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.common.logging.PiiMasker;
import com.flashsale.user.dto.MeResponse;
import com.flashsale.user.entity.User;
import com.flashsale.user.repository.UserRepository;
import com.flashsale.user.service.UserService;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public MeResponse getProfile(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        return new MeResponse(user.getId(), user.getRegion(), user.getRole().name(), user.getStatus().name(),
                PiiMasker.maskEmail(user.getEmail()), PiiMasker.maskPhone(user.getPhone()));
    }
}
