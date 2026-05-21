package com.bank.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.domain.entity.User;
import com.bank.infrastructure.persistence.UserRepository;
import com.bank.service.api.UserService;

@Service
@Transactional
public class UserServiceImpl implements UserService {

	private final UserRepository userRepository;
	
	public UserServiceImpl(UserRepository userRepository) {
		this.userRepository = userRepository;
	}
	@Override
	public List<User> getAllUsers() {
		return userRepository.findAll();
	}
	@Override
	public Optional<User> getUserById(UUID id) {
		return userRepository.findById(id);
	}
		
	@Override
	public User saveUser(User user) {
		return userRepository.save(user);
	}
	@Override
	public Page<User> getAllAds(Pageable page) {
		return userRepository.findAll(page);
	}
	
	@Override
	public void deleteUser(UUID id) {
		userRepository.deleteById(id);
	}
	
	
}
