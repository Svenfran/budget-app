package com.github.svenfran.budgetapp.budgetappbackend.service.mapper

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import spock.lang.Specification

class ShoppingItemDtoMapperSpec extends Specification {

    def mapper = new ShoppingItemDtoMapper()

    def "Einkaufsartikel-DTO wird mit ID, Name, Erledigt-Status und Liste übernommen"() {
        given:
        def list = TestData.shoppingList(id: 1L, group: TestData.group(id: 10L))
        def dto = new AddEditShoppingItemDto(id: 2L, name: 'Milch', completed: true, groupId: 10L)

        when:
        def item = mapper.addEditShoppingItemDtoToEntity(dto, list)

        then:
        item.id == 2L
        item.name == 'Milch'
        item.completed
        item.shoppingList.id == 1L
    }
}
