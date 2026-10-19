package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.ApiRequests
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.springframework.beans.factory.annotation.Autowired

import static java.time.LocalDate.of
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

/**
 * DIV-30: monthName der Jahresübersicht folgt der Sprache aus Accept-Language (de/en/es, Standard de).
 * Das Frontend zeigt monthName nicht an (eigene MonthNamePipe), das Feld ist aber Teil des DTO-Vertrags.
 */
class SpendingsOverviewControllerApiSpec extends IntegrationSpec implements ApiRequests {

    @Autowired ObjectMapper objectMapper

    def "Accept-Language '#header' liefert den Monatsnamen '#expected'"() {
        given:
        def wg = fixtures.group(memberSince: of(2025, 1, 1))
        fixtures.cart(wg, datePurchased: DateUtils.toDate(of(2025, 3, 10)))
        flushAndClear()

        when:
        def request = get('/api/spendings/{groupId}/{year}', wg.group.id, 2025)
        if (header) {
            request.header('Accept-Language', header)
        }
        def response = send(request, user: wg.owner)

        then:
        response.status == 200
        def body = json(response)
        body.spendingsPerMonth*.month == [3]
        body.spendingsPerMonth*.monthName == [expected]

        where:
        header  || expected
        'en'    || 'March'
        'es'    || 'marzo'
        'en-US' || 'March'
        null    || 'März'
    }
}
