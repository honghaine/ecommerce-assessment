package com.flashsale.user.service;

import com.flashsale.user.dto.MeResponse;

public interface UserService {

    /** Profile of the given user with email/phone masked. */
    MeResponse getProfile(long userId);
}
