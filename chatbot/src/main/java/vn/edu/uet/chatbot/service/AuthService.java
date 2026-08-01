package vn.edu.uet.chatbot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.chatbot.dto.auth.*;
import vn.edu.uet.chatbot.entity.Role;
import vn.edu.uet.chatbot.entity.UserEntity;
import vn.edu.uet.chatbot.repository.UserRepository;
import vn.edu.uet.chatbot.security.CustomUserDetailsService;
import vn.edu.uet.chatbot.security.JwtUtil;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username()))
            throw new IllegalArgumentException("Username đã tồn tại.");
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.ROLE_STUDENT);
        userRepository.save(user);
        return new AuthResponse("Đăng ký thành công!", null, null, user.getUsername(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        String token = jwtUtil.generateToken(userDetails);
        return new AuthResponse("Login successful!", token, "Bearer", userDetails.getUsername(),
                userDetails.getAuthorities().stream().findFirst().map(Object::toString).orElse(null));
    }
}