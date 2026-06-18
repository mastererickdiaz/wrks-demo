package com.example.userservice.service;

import com.example.userservice.model.User;
import com.example.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @Test
    void findAllDelegaEnFindByActiveTrue() {
        userService = new UserService(userRepository);
        User active = User.builder().id(1L).name("Ana").email("ana@example.com").active(true).build();
        when(userRepository.findByActiveTrue()).thenReturn(List.of(active));

        List<User> result = userService.findAll();

        assertThat(result).containsExactly(active);
    }

    @Test
    void findByIdDelegaEnFindByIdAndActiveTrue() {
        userService = new UserService(userRepository);
        User active = User.builder().id(1L).name("Ana").email("ana@example.com").active(true).build();
        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(active));

        Optional<User> result = userService.findById(1L);

        assertThat(result).contains(active);
    }

    @Test
    void deleteByIdHaceSoftDeleteMarcandoActiveFalse() {
        userService = new UserService(userRepository);
        User user = User.builder().id(1L).name("Ana").email("ana@example.com").active(true).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deleteById(1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getActive()).isFalse();
    }
}
