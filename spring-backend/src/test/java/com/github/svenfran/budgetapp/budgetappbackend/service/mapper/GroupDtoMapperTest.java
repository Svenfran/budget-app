package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GroupDtoMapperTest {


    private final GroupDtoMapper groupDtoMapper = new GroupDtoMapper();

    @Test
    void groupDtoToEntity_positive() {
        var groupEntity = groupDtoMapper.GroupDtoToEntity(groupDto(), groupOwner());

        assertEquals(groupEntity.getId(), groupDto().getId(), "GroupId should be: 1");
        assertEquals(groupEntity.getName(), groupDto().getName(), "GroupName should be: New Group");
        assertEquals(groupEntity.getOwner().getId(), groupOwner().getId(), "groupOwnerId should be: 5");
        assertEquals(groupEntity.getOwner().getName(), groupOwner().getName(), "Name of groupOwner should be: testUser");

        assertEquals(groupEntity.getId(), 1L, "Group Id should be: 1");
        assertEquals(groupEntity.getName(), "New Group", "Group Name should be: New Group");
        assertEquals(groupEntity.getOwner().getId(), 5L, "Group Owner Id should be: 5");
        assertEquals(groupEntity.getOwner().getName(), "testUser", "Name of Group Owner should be: testUser");
    }

    @Test
    void groupDtoToEntity_negative() {
        var groupEntity = groupDtoMapper.GroupDtoToEntity(groupDto(), groupOwner());

        assertNotEquals(groupEntity.getId(), 5L);
        assertNotEquals(groupEntity.getName(), "Test Group");
        assertNotEquals(groupEntity.getOwner().getId(), 10L);
        assertNotEquals(groupEntity.getOwner().getName(), "owner");
    }

    private User groupOwner() {
        var groupOwner = new User();
        groupOwner.setId(5L);
        groupOwner.setName("testUser");
        return groupOwner;
    }

    private GroupDto groupDto() {
        var groupDto = new GroupDto();
        groupDto.setId(1L);
        groupDto.setName("New Group");
        return groupDto;
    }
}
