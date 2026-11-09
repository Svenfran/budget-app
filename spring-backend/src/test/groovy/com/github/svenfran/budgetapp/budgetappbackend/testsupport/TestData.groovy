package com.github.svenfran.budgetapp.budgetappbackend.testsupport

import com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType
import com.github.svenfran.budgetapp.budgetappbackend.constants.TokenType
import com.github.svenfran.budgetapp.budgetappbackend.constants.TypeEnum
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto
import com.github.svenfran.budgetapp.budgetappbackend.entity.*
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.helper.Translator
import com.github.svenfran.budgetapp.budgetappbackend.helper.export.CartExportRow
import com.github.svenfran.budgetapp.budgetappbackend.helper.export.GroupExport
import com.github.svenfran.budgetapp.budgetappbackend.helper.export.MembershipExportRow
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.context.support.ResourceBundleMessageSource

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream

/**
 * Nicht persistierte, fachlich gültige Testobjekte (Unit-Specs und Bausteine für {@link Fixtures}).
 *
 * - Jedes Feld per Named Argument überschreibbar: TestData.user(name: 'ben').
 * - Namen und E-Mails sind eindeutig (laufende Nummer), Collections initialisiert.
 * - Feste Daten über REFERENCE_DATE statt LocalDate.now(); Umrechnung nur über DateUtils.
 * - Neue Methoden erst mit dem ersten Test, der sie braucht.
 */
class TestData {

    static final LocalDate REFERENCE_DATE = LocalDate.of(2025, 6, 15)
    private static final AtomicLong SEQUENCE = new AtomicLong()

    static long nextId() { SEQUENCE.incrementAndGet() }

    static User user(Map overrides = [:]) {
        long n = nextId()
        new User([name: "user$n", email: "user$n@divvy.test", password: 'secret',
                  groups: [] as Set, groupList: [] as Set, carts: [] as Set] + overrides)
    }

    static Group group(Map overrides = [:]) {
        new Group([name: "Gruppe ${nextId()}", members: [] as Set, carts: [] as Set,
                   categories: [] as Set, shoppingLists: [] as Set] + overrides)
    }

    static Category category(Map overrides = [:]) {
        new Category([name: "Kategorie ${nextId()}"] + overrides)
    }

    static GroupMembershipHistory membership(Map overrides = [:]) {
        new GroupMembershipHistory([type: TypeEnum.MEMBER,
                                    membershipStart: DateUtils.toDate(REFERENCE_DATE.minusYears(1)),
                                    membershipEnd: null] + overrides)
    }

    static Cart cart(Map overrides = [:]) {
        new Cart([title: 'Einkauf', description: 'Wocheneinkauf', amount: 40.0d, averagePerMember: 40.0d,
                  datePurchased: DateUtils.toDate(REFERENCE_DATE)] + overrides)
    }

    static CartTemplate cartTemplate(Map overrides = [:]) {
        new CartTemplate([title: 'Einkauf', description: 'Wocheneinkauf', amount: 40.0d,
                          recurrenceType: RecurrenceType.MONTHLY, active: true, startDate: REFERENCE_DATE,
                          nextExecutionDate: REFERENCE_DATE, endDate: null, anchorDay: REFERENCE_DATE.dayOfMonth] + overrides)
    }

    static ShoppingList shoppingList(Map overrides = [:]) {
        new ShoppingList([name: "Liste ${nextId()}", shoppingItems: [] as Set] + overrides)
    }

    static ShoppingItem shoppingItem(Map overrides = [:]) {
        new ShoppingItem([name: "Eintrag ${nextId()}", completed: false] + overrides)
    }

    /** Kaufereignis; sourceItemId ist negativ und kollidiert so nie mit der ID eines echten (gelöschten) Eintrags. */
    static ShoppingPurchase shoppingPurchase(Map overrides = [:]) {
        new ShoppingPurchase([itemName: "Artikel ${nextId()}", purchasedAt: DateUtils.toDate(REFERENCE_DATE),
                              sourceItemId: -nextId()] + overrides)
    }

    /** Gültiges Token (weder abgelaufen noch widerrufen); Platzhalter statt echtem JWT. Nutzer setzt der Aufrufer. */
    static Token token(Map overrides = [:]) {
        new Token([token: "token-${nextId()}", tokenType: TokenType.BEARER, expired: false, revoked: false,
                   deviceId: 'device-1'] + overrides)
    }

    /**
     * Signiertes JWT wie JwtService.generateToken (Header typ JWT, HS256, Claim deviceId, Subject), aber mit festen
     * Zeitpunkten statt der realen Uhr. Pflicht: secret (Base64 wie secret_key, nur Dummy-Werte) und expiration
     * (Date, Umrechnung über DateUtils.toDate beim Aufrufer); issuedAt ist standardmäßig 21 Tage davor.
     */
    static String jwt(Map overrides = [:]) {
        def values = [subject: "user${nextId()}@divvy.test", deviceId: 'device-1'] + overrides
        assert values.secret && values.expiration: 'TestData.jwt: secret und expiration angeben'
        Date expiration = values.expiration
        Date issuedAt = values.issuedAt ?: new Date(expiration.time - 21L * 24 * 60 * 60 * 1000)
        Jwts.builder()
                .setHeaderParam('typ', 'JWT')
                .setClaims([deviceId: values.deviceId])
                .setSubject(values.subject as String)
                .setIssuedAt(issuedAt)
                .setExpiration(expiration)
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(values.secret as String)), SignatureAlgorithm.HS256)
                .compact()
    }

    static CartDto cartDto(Map overrides = [:]) {
        new CartDto([title: 'Einkauf', description: 'Wocheneinkauf', amount: 40.0d,
                     datePurchased: DateUtils.toDate(REFERENCE_DATE), recurrenceType: RecurrenceType.NONE] + overrides)
    }

    static CategoryDto categoryDto(Category category) {
        new CategoryDto(id: category.id, name: category.name)
    }

    /** Formatneutrale Exportdaten einer Gruppe (DIV-68); Named args: groupId, groupName, carts, memberships. */
    static GroupExport groupExport(Map overrides = [:]) {
        long n = nextId()
        def values = [groupId: n, groupName: "Gruppe $n", carts: [], memberships: []] + overrides
        new GroupExport(values.groupId as Long, values.groupName as String, values.carts as List, values.memberships as List)
    }

    static CartExportRow cartExportRow(Map overrides = [:]) {
        def values = [userName: 'Anna', title: 'Einkauf', description: 'Wocheneinkauf', datePurchased: REFERENCE_DATE,
                      amount: 40.0d, category: 'Lebensmittel', groupName: 'Haushalt'] + overrides
        new CartExportRow(values.userName as String, values.title as String, values.description as String,
                values.datePurchased as LocalDate, values.amount as double, values.category as String, values.groupName as String)
    }

    /** Mitgliedschaft ab einem Jahr vor REFERENCE_DATE, offen (end null). */
    static MembershipExportRow membershipExportRow(Map overrides = [:]) {
        def values = [userName: 'Anna', start: REFERENCE_DATE.minusYears(1), end: null, type: TypeEnum.MEMBER.toString()] + overrides
        new MembershipExportRow(values.userName as String, values.start as LocalDate, values.end as LocalDate, values.type as String)
    }

    /** Export-ZIP zurücklesen (DIV-68): Eintragsname → Inhalt (UTF-8 inkl. BOM), in der Reihenfolge des Archivs. */
    static Map<String, String> unzip(byte[] bytes) {
        def result = [:] as LinkedHashMap<String, String>
        new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8).withCloseable { zip ->
            for (def entry = zip.nextEntry; entry != null; entry = zip.nextEntry) {
                result[entry.name] = new String(zip.readAllBytes(), StandardCharsets.UTF_8)
            }
        }
        result
    }

    /** Export-XLSX zurücklesen (DIV-68). */
    static XSSFWorkbook workbook(byte[] bytes) {
        new XSSFWorkbook(new ByteArrayInputStream(bytes))
    }

    /**
     * Translator auf den echten messages*.properties mit fester Sprache (Unit-Specs ohne Spring und ohne
     * LocaleContextHolder). Fehlende Schlüssel werfen eine Exception statt den Schlüssel zurückzugeben.
     */
    static Translator translator(Locale locale = Locale.GERMAN) {
        def source = new ResourceBundleMessageSource(basenames: ['messages'] as String[], defaultEncoding: 'UTF-8',
                fallbackToSystemLocale: false)
        new Translator(source) {
            @Override
            String translate(String key, Object... args) {
                source.getMessage(key, args, locale)
            }
        }
    }
}
