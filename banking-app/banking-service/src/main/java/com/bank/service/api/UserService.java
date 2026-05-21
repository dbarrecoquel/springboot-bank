package com.bank.service.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.bank.domain.entity.User;

public interface UserService {

	public List<User> getAllUsers();
	public Optional<User> getUserById(UUID id);
	public User saveUser(User user);
	public Page<User> getAllAds(Pageable page);
	public void deleteUser(UUID id);

}
