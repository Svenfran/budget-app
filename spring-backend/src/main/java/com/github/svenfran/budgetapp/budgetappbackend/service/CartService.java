package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.*;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils;
import com.github.svenfran.budgetapp.budgetappbackend.helper.RecurrenceCalculator;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartTemplateRepository;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.CartDtoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class CartService {

    private final Logger logger = LoggerFactory.getLogger(CartService.class);

    private final CartRepository cartRepository;

    private final CartDtoMapper cartDtoMapper;

    private final DataLoaderService dataLoaderService;

    private final VerificationService verificationService;

    private final CartTemplateRepository templateRepository;

    private final Clock clock;

    public CartService(CartRepository cartRepository, CartDtoMapper cartDtoMapper, DataLoaderService dataLoaderService,
                       VerificationService verificationService, CartTemplateRepository templateRepository, Clock clock) {
        this.cartRepository = cartRepository;
        this.cartDtoMapper = cartDtoMapper;
        this.dataLoaderService = dataLoaderService;
        this.verificationService = verificationService;
        this.templateRepository = templateRepository;
        this.clock = clock;
    }

    public List<CartDto> getCartsByGroupId(Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(groupId);
        verificationService.verifyIsPartOfGroup(user, group);
        var cartList = dataLoaderService.loadCartListForGroup(groupId);
        return cartList.stream().map(CartDto::new).toList();
    }

    public CartDto getCartById(Long id) throws CartNotFoundException, UserNotFoundException, NotOwnerOrMemberOfGroupException, GroupNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        var cart = dataLoaderService.loadCart(id);
        var group = dataLoaderService.loadGroup(cart.getGroup().getId());
        verificationService.verifyIsPartOfGroup(user, group);
        return new CartDto(cart);
    }

    @Transactional
    public CartDto addCart(@Validated CartDto cartDto) throws UserNotFoundException, CategoryNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, DatePurchasedNotWithinMembershipPeriodException, SettlementCategoryNotAllowedException {
        var user = dataLoaderService.getAuthenticatedUser();
        var category = dataLoaderService.loadCategory(cartDto.getCategoryDto().getId());
        var group = dataLoaderService.loadGroup(cartDto.getGroupId());
        var gmh = dataLoaderService.loadMembershipHistoryForGroupAndUser(group.getId(), user.getId());
        verificationService.verifyIsPartOfGroup(user, group);
        // Ausgleichszahlungen nur über SettlementService (DIV-74)
        verificationService.verifyCategoryIsNotSettlement(category);
        verificationService.verifyDatePurchasedWithinMembershipPeriod(gmh, cartDto.getDatePurchased());
        var groupMemberCount = dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(cartDto.getDatePurchased(), group.getId());
        var cart = cartDtoMapper.cartDtoToEntity(cartDto, category, user, group, groupMemberCount);
        // Neuanlage erzwingen (DIV-73): mit gesetzter ID würde save() per merge einen bestehenden, auch fremden Cart
        // überschreiben – ohne Eigentümerprüfung. Der Mapper behält die ID, weil updateCart sie braucht.
        cart.setId(null);
        createTemplateForCartIfRecurrenceTypeValid(cart, user, group, cartDto.getRecurrenceType());
        return new CartDto(cartRepository.save(cart));
    }

    @Transactional
    public CartDto updateCart(@Validated CartDto cartDto) throws UserNotFoundException, CategoryNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, DatePurchasedNotWithinMembershipPeriodException, CartNotFoundException, NotOwnerOfCartException, SettlementCartNotEditableException, SettlementCategoryNotAllowedException {
        var user = dataLoaderService.getAuthenticatedUser();
        var cart = dataLoaderService.loadCart(cartDto.getId());
        var group = dataLoaderService.loadGroup(cartDto.getGroupId());
        var gmh = dataLoaderService.loadMembershipHistoryForGroupAndUser(group.getId(), user.getId());
        verificationService.verifyIsPartOfGroup(user, group);
        verificationService.verifyIsOwnerOfCart(user, cart);
        // Ausgleichszahlungen nur über SettlementService (DIV-74): weder Settlement-Carts noch die Settlement-Kategorie
        verificationService.verifyCartIsNotSettlement(cart);
        verificationService.verifyDatePurchasedWithinMembershipPeriod(gmh, cartDto.getDatePurchased());
        var category = dataLoaderService.loadCategory(cartDto.getCategoryDto().getId());
        verificationService.verifyCategoryIsNotSettlement(category);
        var groupMemberCount = dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(cartDto.getDatePurchased(), group.getId());
        return updateCartAndTemplateIfValid(cart, cartDto, user, group, category, groupMemberCount);
    }

    // Transaktional (DIV-74): Template-Deaktivierung und Löschen gemeinsam
    @Transactional
    public void deleteCart(Long id) throws UserNotFoundException, CartNotFoundException, GroupNotFoundException, NotOwnerOfCartException, NotOwnerOrMemberOfGroupException, SettlementCartNotEditableException {
        var user = dataLoaderService.getAuthenticatedUser();
        var cart = dataLoaderService.loadCart(id);
        var group = dataLoaderService.loadGroup(cart.getGroup().getId());
        verificationService.verifyIsPartOfGroup(user, group);
        verificationService.verifyIsOwnerOfCart(user, cart);
        // Ausgleichszahlungen nur paarweise über SettlementService löschen (DIV-74)
        verificationService.verifyCartIsNotSettlement(cart);
        deactivateCartTemplateIfCartDeleted(cart);
        cartRepository.deleteById(id);
    }

    // Ausgleichszahlungen: SettlementService (DIV-74)

    // ----- Wiederholende Einträge -----

    @Scheduled(cron = "${task.recurringCart.schedule}")
    @Transactional
    public void generateRecurringCarts() throws UserNotFoundException, GroupNotFoundException, CategoryNotFoundException {
        List<CartTemplate> templates = templateRepository.findByActiveTrue();
        var today = LocalDate.now(clock);

        for (CartTemplate template : templates) {
            if (template.getRecurrenceType() == null || template.getRecurrenceType() == RecurrenceType.NONE) {
                logger.warn("Aktives CartTemplate {} ohne Wiederholung wird übersprungen", template.getId());
                continue;
            }

            var nextDate = template.getNextExecutionDate();
            if (nextDate.isAfter(today)) {
                continue;
            }

            // Verpasste Termine (z. B. Job lief nicht) werden einzeln nachgeholt
            while (!nextDate.isAfter(today)) {
                var groupMemberCount = dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(asDate(nextDate), template.getGroupId());
                if (groupMemberCount == 0) {
                    logger.warn("CartTemplate {} wird deaktiviert: keine Mitglieder in Gruppe {} am {}", template.getId(), template.getGroupId(), nextDate);
                    template.setActive(false);
                    template.setEndDate(today);
                    break;
                }
                if (checkIfCartAlreadyExists(template, nextDate) < 1) {
                    if (nextDate.isBefore(today)) {
                        logger.info("CartTemplate {}: verpasster Termin {} wird nachgeholt", template.getId(), nextDate);
                    }
                    createCartFromTemplate(template, nextDate, groupMemberCount);
                }
                nextDate = RecurrenceCalculator.nextOccurrence(nextDate, template.getRecurrenceType(), template.getAnchorDay());
            }
            // Auch nach Deaktivierung setzen, damit der Abbruchtermin erhalten bleibt
            template.setNextExecutionDate(nextDate);
            templateRepository.save(template);
        }
    }

    private int checkIfCartAlreadyExists(CartTemplate template, LocalDate date) {
        return cartRepository.numberOfSimilarCarts(
                template.getTitle(),
                template.getDescription(),
                template.getCategoryId(),
                asDate(date),
                template.getAmount()
        );
    }

    public void createTemplateForCartIfRecurrenceTypeValid(Cart cart, User user, Group group, RecurrenceType recurrenceType) {
        if (recurrenceType != null && !recurrenceType.equals(RecurrenceType.NONE)) {
            var cartDto = new CartDto(cart);
            createNewCartTemplate(cartDto, user, group, cart, recurrenceType);
        }
    }

    private void deactivateCartTemplateIfCartDeleted(Cart cart) {
        if (cart.getTemplate() != null && cartRepository.numberOfCartsWithActiveTemplate(cart.getTemplate().getId()) <= 1) {
            deactivateCartTemplate(cart);
        }
    }

    private void createCartFromTemplate(CartTemplate template, LocalDate nextDate, int groupMemberCount) throws UserNotFoundException, GroupNotFoundException, CategoryNotFoundException {
        var newCart = new Cart();
        var user = dataLoaderService.loadUser(template.getUserId());
        var group = dataLoaderService.loadGroup(template.getGroupId());
        var category = dataLoaderService.loadCategory(template.getCategoryId());
        newCart.setUser(user);
        newCart.setGroup(group);
        newCart.setTitle(template.getTitle());
        newCart.setDescription(template.getDescription());
        newCart.setAmount(template.getAmount());
        newCart.setAveragePerMember(template.getAmount() / groupMemberCount);
        newCart.setDescription(template.getDescription());
        newCart.setCategory(category);
        newCart.setDatePurchased(asDate(nextDate));
        newCart.setTemplate(template);

        cartRepository.save(newCart);
    }

    // Ankertag für ein (neues) Template aus dem Einkauf: bisheriger Anker nur bei unverändertem Kaufdatum (DIV-25, E3)
    private int resolveAnchorDay(LocalDate newDate, Cart cart) {
        LocalDate previousDate = cart.getDatePurchased() != null ? asLocalDate(cart.getDatePurchased()) : null;
        Integer previousAnchorDay = cart.getTemplate() != null ? cart.getTemplate().getAnchorDay() : null;
        return RecurrenceCalculator.resolveAnchorDay(newDate, previousDate, previousAnchorDay);
    }

    private void deactivateCartTemplate(Cart cart) {
        cart.getTemplate().setActive(false);
        cart.getTemplate().setEndDate(LocalDate.now(clock));
    }

    private void createNewCartTemplate(@Validated CartDto cartDto, User user, Group group, Cart cart, RecurrenceType recurrenceType) {
        CartTemplate newTemplate = new CartTemplate();
        newTemplate.setUserId(user.getId());
        newTemplate.setGroupId(group.getId());
        newTemplate.setCategoryId(cartDto.getCategoryDto().getId());
        newTemplate.setTitle(cartDto.getTitle());
        if (cartDto.getDatePurchased() != null) {
            newTemplate.setDescription(cartDto.getDescription());
        }
        newTemplate.setAmount(cartDto.getAmount());
        newTemplate.setRecurrenceType(recurrenceType);
        newTemplate.setActive(true);
        newTemplate.setStartDate(LocalDate.now(clock));
        // cart trägt hier noch Kaufdatum und Template vor der Bearbeitung
        var datePurchased = asLocalDate(cartDto.getDatePurchased());
        var anchorDay = resolveAnchorDay(datePurchased, cart);
        newTemplate.setAnchorDay(anchorDay);
        newTemplate.setNextExecutionDate(
                RecurrenceCalculator.firstOccurrenceAfter(datePurchased, recurrenceType, anchorDay, LocalDate.now(clock))
        );
        templateRepository.save(newTemplate);
        cart.setTemplate(newTemplate);
    }

    public CartDto updateCartAndTemplateIfValid(Cart cart, CartDto cartDto, User user, Group group, Category category, int groupMemberCount) {
        // --- Flags ---
        boolean cartChanged = hasCartChanged(cartDto, cart);
        boolean recurrenceTypeChanged = hasRecurrenceTypeChanged(cartDto, cart);
        boolean updateSelected = cartDto.isTemplateUpdateSelected();
        boolean templateChanged = hasTemplateChanged(cartDto, cart);

        // --------- Case: Cart hat sich geändert ----------
        if (cartChanged) {
            if (recurrenceTypeChanged) {
                handleRecurrenceTypeChange(cart, cartDto, user, group);
            }

            if (updateSelected && templateChanged) {
                handleTemplateUpdate(cart, cartDto, user, group);
            }

            var updatedCart = cartDtoMapper.cartDtoToEntity(cartDto, category, user, group, groupMemberCount);
            updatedCart.setTemplate(cart.getTemplate());

            var dto = new CartDto(cartRepository.save(updatedCart));
            if (templateChanged) { dto.setHasTemplateChanged(true); }
            return dto;
        }

        // --------- Case: Cart hat sich NICHT geändert ----------
        if (recurrenceTypeChanged) {
            handleRecurrenceTypeChange(cart, cartDto, user, group);
        }

        if (updateSelected && templateChanged) {
            handleTemplateUpdate(cart, cartDto, user, group);
        }

        var dto = new CartDto(cart);
        if (templateChanged) { dto.setHasTemplateChanged(true); }
        return dto;
    }

    private void handleRecurrenceTypeChange(Cart cart, CartDto dto, User user, Group group) {
        RecurrenceType oldType = (cart.getTemplate() != null && cart.getTemplate().isActive())
                ? cart.getTemplate().getRecurrenceType()
                : RecurrenceType.NONE;

        RecurrenceType newType = normalizedRecurrenceType(dto);

        if (oldType != newType) {
            if (cart.getTemplate() != null && cart.getTemplate().isActive()) {
                deactivateTemplate(cart.getTemplate());
            }

            if (newType != RecurrenceType.NONE) {
                createNewCartTemplate(dto, user, group, cart, newType);
            }
        }
    }

    private void handleTemplateUpdate(Cart cart, CartDto dto, User user, Group group) {
        if (cart.getTemplate() != null && cart.getTemplate().isActive()) {
            deactivateTemplate(cart.getTemplate());
        }

        RecurrenceType newType = normalizedRecurrenceType(dto);
        if (newType != RecurrenceType.NONE) {
            createNewCartTemplate(dto, user, group, cart, newType);
        }
    }

    private void deactivateTemplate(CartTemplate template) {
        template.setActive(false);
        template.setEndDate(LocalDate.now(clock));
        templateRepository.save(template);
    }

    private boolean hasCartChanged(CartDto dto, Cart entity) {
        if (!Objects.equals(dto.getTitle(), entity.getTitle())) return true;
        if (!Objects.equals(dto.getDescription(), entity.getDescription())) return true;
        if (!Objects.equals(dto.getAmount(), entity.getAmount())) return true;
        if (!Objects.equals(asLocalDate(dto.getDatePurchased()), asLocalDate(entity.getDatePurchased()))) return true;
        if (!Objects.equals(dto.getCategoryDto().getId(), entity.getCategory().getId())) return true;

        return false;
    }

    private boolean hasTemplateChanged(CartDto dto, Cart cart) {
        CartTemplate tpl = cart.getTemplate();
        RecurrenceType newType = normalizedRecurrenceType(dto);

        if (tpl == null) {
            return newType != RecurrenceType.NONE;
        }

        if (!Objects.equals(tpl.getRecurrenceType(), newType)) return true;
        if (!Objects.equals(tpl.getAmount(), dto.getAmount())) return true;
        if (!Objects.equals(tpl.getTitle(), dto.getTitle())) return true;
        if (!Objects.equals(tpl.getDescription(), dto.getDescription())) return true;
        if (!Objects.equals(tpl.getCategoryId(), dto.getCategoryDto().getId())) return true;
        // Ohne Wiederholung gibt es kein Folgedatum
        if (newType != RecurrenceType.NONE) {
            var datePurchased = asLocalDate(dto.getDatePurchased());
            var anchorDay = resolveAnchorDay(datePurchased, cart);
            var nextDate = RecurrenceCalculator.firstOccurrenceAfter(datePurchased, newType, anchorDay, LocalDate.now(clock));
            if (!Objects.equals(tpl.getNextExecutionDate(), nextDate)) return true;
            // Verschiedene Ankertage können denselben nächsten Termin ergeben (30. und 31. → 28.02.)
            if ((newType == RecurrenceType.MONTHLY || newType == RecurrenceType.YEARLY)
                    && !Objects.equals(tpl.getAnchorDay(), anchorDay)) return true;
        }

        return false;
    }

    private boolean hasRecurrenceTypeChanged(CartDto dto, Cart cart) {
        RecurrenceType oldType = (cart.getTemplate() != null && cart.getTemplate().isActive())
                ? cart.getTemplate().getRecurrenceType()
                : RecurrenceType.NONE;
        RecurrenceType newType = normalizedRecurrenceType(dto);
        return oldType != newType;
    }

    private RecurrenceType normalizedRecurrenceType(CartDto dto) {
        return dto.getRecurrenceType() != null ? dto.getRecurrenceType() : RecurrenceType.NONE;
    }

    private Date asDate(LocalDate date) {
        return DateUtils.toDate(date);
    }

    private LocalDate asLocalDate(Date date) {
        return DateUtils.toLocalDate(date);
    }
}
