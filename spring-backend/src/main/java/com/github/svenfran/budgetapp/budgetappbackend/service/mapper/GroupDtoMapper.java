package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import org.springframework.stereotype.Service;

@Service
public class GroupDtoMapper {

    public Group GroupDtoToEntity(GroupDto dto, User owner) {
        var group = new Group();
        group.setId(dto.getId());
        group.setName(dto.getName());
        group.setOwner(owner);
        return group;
    }
}
