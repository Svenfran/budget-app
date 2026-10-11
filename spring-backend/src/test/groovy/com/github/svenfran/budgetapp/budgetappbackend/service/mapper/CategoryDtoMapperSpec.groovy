package com.github.svenfran.budgetapp.budgetappbackend.service.mapper

import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import spock.lang.Specification

class CategoryDtoMapperSpec extends Specification {

    def mapper = new CategoryDtoMapper()

    def "Kategorie-DTO wird mit ID, Name und Gruppe übernommen"() {
        when:
        def category = mapper.CategoryDtoToEntity(new CategoryDto(id: 1L, name: 'Lebensmittel'), TestData.group(id: 10L))

        then:
        category.id == 1L
        category.name == 'Lebensmittel'
        category.group.id == 10L
    }
}
