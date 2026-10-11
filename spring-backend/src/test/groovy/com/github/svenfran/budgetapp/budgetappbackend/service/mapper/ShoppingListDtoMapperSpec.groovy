package com.github.svenfran.budgetapp.budgetappbackend.service.mapper

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingListDto
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import spock.lang.Specification

class ShoppingListDtoMapperSpec extends Specification {

    def mapper = new ShoppingListDtoMapper()

    def "Einkaufslisten-DTO wird mit ID, Name und Gruppe übernommen"() {
        when:
        def list = mapper.addEditShoppingListDtoToEntity(new AddEditShoppingListDto(id: 1L, name: 'Wochenmarkt', groupId: 10L),
                TestData.group(id: 10L))

        then:
        list.id == 1L
        list.name == 'Wochenmarkt'
        list.group.id == 10L
    }
}
