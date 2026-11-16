package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.*;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.helper.Translator;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CategoryRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Ausgleichszahlungen (DIV-74, vorher in CartService). Das {@link Settlement} ist die einzige Wahrheit; die beiden Carts
 * (Zahler +amount, Empfänger -amount, gleiches Datum, Settlement-Kategorie der Gruppe) sind daraus abgeleitete Buchungen
 * und werden nur hier geschrieben – immer beide in derselben Transaktion.
 * <p>
 * Ablauf je Methode: angemeldeten User laden → Request-Struktur prüfen (vor jedem Loader mit Request-Id) → Entities laden
 * → fachliche Prüfungen → Writes. Carts werden nicht live synchronisiert, daher keine Notification.
 */
@Service
public class SettlementService {

    static final String SETTLEMENT_CATEGORY_KEY = "categories.default.settlement";

    private final SettlementRepository settlementRepository;
    private final CartRepository cartRepository;
    private final CategoryRepository categoryRepository;
    private final DataLoaderService dataLoaderService;
    private final VerificationService verificationService;
    private final Translator translator;

    public SettlementService(SettlementRepository settlementRepository, CartRepository cartRepository,
                             CategoryRepository categoryRepository, DataLoaderService dataLoaderService,
                             VerificationService verificationService, Translator translator) {
        this.settlementRepository = settlementRepository;
        this.cartRepository = cartRepository;
        this.categoryRepository = categoryRepository;
        this.dataLoaderService = dataLoaderService;
        this.verificationService = verificationService;
        this.translator = translator;
    }

    /**
     * Legt ein Settlement und seine beiden Carts an. Zahler ist der angemeldete Nutzer, {@code member} der Empfänger.
     *
     * @return Zahler-Cart, dann Empfänger-Cart (beide mit {@code settlementId})
     */
    @Transactional
    public List<CartDto> addSettlementPayment(SettlementPaymentDto dto) throws UserNotFoundException, GroupNotFoundException,
            NotOwnerOrMemberOfGroupException, DatePurchasedNotWithinMembershipPeriodException, InvalidSettlementPaymentException {
        var user = dataLoaderService.getAuthenticatedUser();
        verificationService.verifyValidSettlementPaymentForCreate(dto);
        var group = dataLoaderService.loadGroup(dto.getGroupId());
        var payer = user;
        var receiver = dataLoaderService.loadUser(dto.getMember().getId());
        verificationService.verifyPayerIsNotReceiver(payer, receiver);
        verificationService.verifyIsPartOfGroup(payer, group);
        verificationService.verifyIsPartOfGroup(receiver, group);
        verifyPartiesWereMembersAt(group, payer, receiver, dto.getDatePurchased());

        var settlement = new Settlement();
        settlement.setGroup(group);
        settlement.setPayer(payer);
        settlement.setReceiver(receiver);
        settlement.setCreatedBy(user);
        settlement.setAmount(dto.getAmount());
        settlement.setDatePurchased(dto.getDatePurchased());
        settlementRepository.save(settlement);

        var category = settlementCategory(group);
        var payerCart = new Cart();
        var receiverCart = new Cart();
        payerCart.setSettlement(settlement);
        receiverCart.setSettlement(settlement);
        projectCarts(settlement, payerCart, receiverCart, category);
        cartRepository.save(payerCart);
        cartRepository.save(receiverCart);
        return List.of(new CartDto(payerCart), new CartDto(receiverCart));
    }

    private void verifyPartiesWereMembersAt(Group group, User payer, User receiver, Date date)
            throws DatePurchasedNotWithinMembershipPeriodException {
        verificationService.verifyDatePurchasedWithinMembershipPeriod(
                dataLoaderService.loadMembershipHistoryForGroupAndUser(group.getId(), payer.getId()), date);
        verificationService.verifyDatePurchasedWithinMembershipPeriod(
                dataLoaderService.loadMembershipHistoryForGroupAndUser(group.getId(), receiver.getId()), date);
    }

    /**
     * Setzt beide Carts vollständig aus dem Settlement: Zahler +amount, Empfänger -amount, gleiches Datum, Gruppe,
     * Kategorie, Titel in Request-Sprache und Legacy-Anteil je Mitglied. {@code isDeleted} bleibt unverändert.
     */
    private void projectCarts(Settlement settlement, Cart payerCart, Cart receiverCart, Category category) {
        var memberCount = dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(
                settlement.getDatePurchased(), settlement.getGroup().getId());
        projectCart(payerCart, settlement, category, settlement.getPayer(), settlement.getAmount(), memberCount,
                translator.translate("cart.settlement_to") + " " + formatUsername(settlement.getReceiver().getName()));
        projectCart(receiverCart, settlement, category, settlement.getReceiver(), -settlement.getAmount(), memberCount,
                translator.translate("cart.settlement_from") + " " + formatUsername(settlement.getPayer().getName()));
    }

    private static void projectCart(Cart cart, Settlement settlement, Category category, User user, double amount,
                                    int memberCount, String title) {
        cart.setTitle(title);
        cart.setDescription("");
        cart.setAmount(amount);
        cart.setAveragePerMember(memberCount > 0 ? amount / memberCount : 0.0);
        cart.setDatePurchased(settlement.getDatePurchased());
        cart.setGroup(settlement.getGroup());
        cart.setCategory(category);
        cart.setUser(user);
    }

    /** Settlement-Kategorie der Gruppe per Kennzeichen; fehlt sie, wird sie mit Namen in Request-Sprache angelegt. */
    private Category settlementCategory(Group group) {
        return categoryRepository.findFirstByGroupAndSettlementTrueOrderByIdAsc(group).orElseGet(() -> {
            var category = new Category(null, translator.translate(SETTLEMENT_CATEGORY_KEY), group, null);
            category.setSettlement(true);
            return categoryRepository.save(category);
        });
    }

    private static String formatUsername(String name) {
        if (name != null && name.length() > 10) {
            return name.substring(0, 10) + "...";
        }
        return name;
    }
}
