package com.rmbreeding.api.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    default UserDto toDto(UUID id, String email, String firstName, String lastName,
            List<String> roles, Instant createdAt) {
        return new UserDto(id, email, firstName, lastName, roles, createdAt);
    }
}
