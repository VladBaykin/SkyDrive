package com.baykin.cloud_storage.skydrive.service;

import com.baykin.cloud_storage.skydrive.dto.AuthRequest;
import com.baykin.cloud_storage.skydrive.exception.AccessDeniedException;
import com.baykin.cloud_storage.skydrive.exception.UserAlreadyExistsException;
import com.baykin.cloud_storage.skydrive.exception.UserNotFoundException;
import com.baykin.cloud_storage.skydrive.model.Role;
import com.baykin.cloud_storage.skydrive.model.User;
import com.baykin.cloud_storage.skydrive.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Сервис для регистрации и работы с пользователями.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Возвращает корневой путь пользователя для хранения файлов.
     *
     * @param userId идентификатор пользователя
     * @return строка с корневым путём пользователя (например, user-1-files/)
     */
    public String getUserRoot(Long userId) {
        return "user-" + userId + "-files/";
    }

    /**
     * Регистрирует нового пользователя.
     *
     * @param request DTO с данными для регистрации (логин и пароль)
     * @return созданный пользователь
     * @throws UserAlreadyExistsException если пользователь с таким именем уже существует
     */
    public User register(AuthRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new UserAlreadyExistsException("The user already exists!");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();
        return userRepository.save(user);
    }

    /**
     * Получает пользователя по имени пользователя.
     *
     * @param username имя пользователя
     * @return объект пользователя
     * @throws UserNotFoundException если пользователь не найден
     */
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    /**
     * Получает идентификатор пользователя по имени пользователя.
     *
     * @param username имя пользователя
     * @return идентификатор пользователя
     * @throws UserNotFoundException если пользователь не найден
     */
    public Long getUserIdByUsername(String username) {
        User user = getUserByUsername(username);
        return user.getId();
    }

    /**
     * Возвращает имя пользователя текущей аутентифицированной сессии.
     *
     * @return имя пользователя
     * @throws AccessDeniedException если пользователь не авторизован
     */
    public String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Пользователь не авторизован");
        }
        return auth.getName();
    }
}