package com.bank.security;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.bank.domain.entity.User;
import com.bank.infrastructure.persistence.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
	
	private final UserRepository userRepository;
	
	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		
		User user = userRepository.findByEmailWithRoles(username).orElseThrow(() -> {
            log.warn("[SECURITY] Utilisateur introuvable — email={}", username);
            // Message générique — ne pas révéler si l'email existe
            return new UsernameNotFoundException("Identifiants invalides");
        });
		
        Set<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getAuthority()))
                .collect(Collectors.toSet());
     
            log.debug("[SECURITY] UserDetails chargé — userId={} roles={}",
                      user.getId(), user.getRoles());
     
            return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(user.isAccountLocked())
                .credentialsExpired(false)
                .disabled(!user.isEnabled())
                .build();

	}

}
