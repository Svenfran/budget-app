package com.github.svenfran.budgetapp.budgetappbackend.service.mapper

import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupDto
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import spock.lang.Specification

class GroupDtoMapperSpec extends Specification {

    def mapper = new GroupDtoMapper()

    def "Gruppen-DTO wird mit ID, Name und Owner übernommen"() {
        when:
        def group = mapper.GroupDtoToEntity(new GroupDto(id: 1L, name: 'WG'), TestData.user(id: 5L))

        then:
        group.id == 1L
        group.name == 'WG'
        group.owner.id == 5L
    }
}
