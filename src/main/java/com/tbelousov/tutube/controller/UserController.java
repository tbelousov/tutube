package com.tbelousov.tutube.controller;

import com.tbelousov.tutube.dto.UserDto;
import com.tbelousov.tutube.entity.User;
import com.tbelousov.tutube.mapper.UserMapper;
import com.tbelousov.tutube.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @GetMapping
    public List<UserDto> getAllUsers() {
        return userRepository.streamAll()
                .map(userMapper::toDto)
                .toList();
    }

    @PostMapping
    public ResponseEntity<UserDto> createUser(@RequestBody UserDto userDto) {
        User user = userMapper.toEntity(userDto);
        User saved = userRepository.save(user);
        return ResponseEntity.ok(userMapper.toDto(saved));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(userMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id, @RequestBody UserDto userDto) {
        var user = userMapper.toEntity(userDto);
        int updated = userRepository.updateUser(id, user);
        return updated > 0
                ? ResponseEntity.ok(userDto)
                : ResponseEntity.notFound().build();
    }
}