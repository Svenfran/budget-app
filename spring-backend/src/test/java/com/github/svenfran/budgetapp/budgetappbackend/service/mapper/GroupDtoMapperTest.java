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

        assertEquals(groupDto().getId(), groupEntity.getId(),"GroupId should be: 1");
        assertEquals(groupDto().getName(), groupEntity.getName(),"GroupName should be: New Group");
        assertEquals(groupOwner().getId(), groupEntity.getOwner().getId(),"groupOwnerId should be: 5");
        assertEquals(groupOwner().getName(), groupEntity.getOwner().getName(),"Name of groupOwner should be: testUser");

        assertEquals(1L, groupEntity.getId(), "Group Id should be: 1");
        assertEquals("New Group", groupEntity.getName(), "Group Name should be: New Group");
        assertEquals(5L, groupEntity.getOwner().getId(), "Group Owner Id should be: 5");
        assertEquals("testUser", groupEntity.getOwner().getName(), "Name of Group Owner should be: testUser");
    }

    @Test
    void groupDtoToEntity_negative() {
        var groupEntity = groupDtoMapper.GroupDtoToEntity(groupDto(), groupOwner());

        assertNotEquals(5L, groupEntity.getId());
        assertNotEquals("Test Group", groupEntity.getName());
        assertNotEquals(10L, groupEntity.getOwner().getId());
        assertNotEquals("owner", groupEntity.getOwner().getName());
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
