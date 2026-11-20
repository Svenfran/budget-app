package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.constants.SupportedCurrency;
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import org.springframework.stereotype.Service;

@Service
public class GroupDtoMapper {

    /** Neue Gruppe aus dem Request; {@code currency == null} (nicht gesendet) → Standardwährung EUR (DIV-77). */
    public Group GroupDtoToEntity(GroupDto dto, User owner, SupportedCurrency currency) {
        var group = new Group();
        group.setId(dto.getId());
        group.setName(dto.getName());
        group.setOwner(owner);
        group.setCurrencyCode(currency != null ? currency : SupportedCurrency.DEFAULT);
        return group;
    }
}
