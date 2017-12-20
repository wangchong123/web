package com.wangchong.service;

import mvc.annotation.Service;

@Service
public class UserService implements IUserService {
    @Override
    public String get(String name) {
       return name;
    }
}
