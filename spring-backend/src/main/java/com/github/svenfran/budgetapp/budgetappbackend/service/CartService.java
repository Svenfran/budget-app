package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.*;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.helper.ExcelWriter;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartTemplateRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CategoryRepository;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.CartDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class CartService {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CartDtoMapper cartDtoMapper;
    
    @Autowired
    private DataLoaderService dataLoaderService;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private CartTemplateRepository templateRepository;

    private static final String SETTLEMENT_CATEGORY_NAME = "Ausgleichszahlung";

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
    public CartDto addCart(@Validated CartDto cartDto) throws UserNotFoundException, CategoryNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, DatePurchasedNotWithinMembershipPeriodException {
        var user = dataLoaderService.getAuthenticatedUser();
        var category = dataLoaderService.loadCategory(cartDto.getCategoryDto().getId());
        var group = dataLoaderService.loadGroup(cartDto.getGroupId());
        var gmh = dataLoaderService.loadMembershipHistoryForGroupAndUser(group.getId(), user.getId());
        verificationService.verifyIsPartOfGroup(user, group);
        verificationService.verifyDatePurchasedWithinMembershipPeriod(gmh, cartDto.getDatePurchased());
        var groupMemberCount = dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(cartDto.getDatePurchased(), group.getId());
        var cart = cartDtoMapper.cartDtoToEntity(cartDto, category, user, group, groupMemberCount);
        createTemplateForCartIfRecurrenceTypeValid(cart, user, group, cartDto.getRecurrenceType());
        return new CartDto(cartRepository.save(cart));
    }

    @Transactional
    public CartDto updateCart(@Validated CartDto cartDto) throws UserNotFoundException, CategoryNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, DatePurchasedNotWithinMembershipPeriodException, CartNotFoundException, NotOwnerOfCartException {
        var user = dataLoaderService.getAuthenticatedUser();
        var cart = dataLoaderService.loadCart(cartDto.getId());
        var group = dataLoaderService.loadGroup(cartDto.getGroupId());
        var gmh = dataLoaderService.loadMembershipHistoryForGroupAndUser(group.getId(), user.getId());
        verificationService.verifyIsPartOfGroup(user, group);
        verificationService.verifyIsOwnerOfCart(user, cart);
        verificationService.verifyDatePurchasedWithinMembershipPeriod(gmh, cartDto.getDatePurchased());
        var category = dataLoaderService.loadCategory(cartDto.getCategoryDto().getId());
        var groupMemberCount = dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(cartDto.getDatePurchased(), group.getId());
        return updateCartAndTemplateIfValid(cart, cartDto, user, group, category, groupMemberCount);
    }

    public void deleteCart(Long id) throws UserNotFoundException, CartNotFoundException, GroupNotFoundException, NotOwnerOfCartException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var cart = dataLoaderService.loadCart(id);
        var group = dataLoaderService.loadGroup(cart.getGroup().getId());
        verificationService.verifyIsPartOfGroup(user, group);
        verificationService.verifyIsOwnerOfCart(user, cart);
        deactivateCartTemplateIfCartDeleted(cart);
        cartRepository.deleteById(id);
    }

    @Transactional
    public List<CartDto> addSettlementPayment(@Validated SettlementPaymentDto settlementPaymentDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, DatePurchasedNotWithinMembershipPeriodException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(settlementPaymentDto.getGroupId());
        var member = dataLoaderService.loadUser(settlementPaymentDto.getMember().getId());
        var gmhUser = dataLoaderService.loadMembershipHistoryForGroupAndUser(group.getId(), user.getId());
        var gmhMember = dataLoaderService.loadMembershipHistoryForGroupAndUser(group.getId(), member.getId());
        verificationService.verifyIsPartOfGroup(user, group);
        verificationService.verifyIsPartOfGroup(member, group);
        verificationService.verifyDatePurchasedWithinMembershipPeriod(gmhUser, settlementPaymentDto.getDatePurchased());
        verificationService.verifyDatePurchasedWithinMembershipPeriod(gmhMember, settlementPaymentDto.getDatePurchased());
        createCategoryForSettlementPaymentIfNotExist(group);
        var category = dataLoaderService.loadCategoryByGroupAndName(group, SETTLEMENT_CATEGORY_NAME);
        var groupMemberCount = dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(settlementPaymentDto.getDatePurchased(), group.getId());
        return createSettlementPaymentCarts(category, user, member, group, settlementPaymentDto.getAmount(), groupMemberCount, settlementPaymentDto.getDatePurchased());
    }

    public void getExcelFile(HttpServletResponse response, Long groupId) throws IOException, GroupNotFoundException, UserNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(groupId);
        verificationService.verifyIsPartOfGroup(user, group);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Expires", "0");
        var cartlist = dataLoaderService.loadCartListForGroup(groupId);
        var membershipHistoryList = dataLoaderService.loadMembershipHistoryForGroup(groupId);
        var excelWriter = new ExcelWriter(cartlist, membershipHistoryList, dataLoaderService);
        excelWriter.generateExcelFile(response);
    }

    private List<CartDto> createSettlementPaymentCarts(Category category, User user, User member, Group group, Double amount, int groupMemberCount, Date datePurchased) {
        var cartDtoSender = new CartDto();
        cartDtoSender.setTitle("Ausgleichszahlung an " + formatUsername(member.getName()));
        cartDtoSender.setDescription("");
        cartDtoSender.setDatePurchased(datePurchased);
        cartDtoSender.setAmount(amount);
        cartDtoSender.setUserDto(new UserDto(user));
        cartDtoSender.setGroupId(group.getId());
        cartDtoSender.setCategoryDto(new CategoryDto(category));
        var cartSender = cartRepository.save(cartDtoMapper.cartDtoToEntity(cartDtoSender, category, user, group, groupMemberCount));
        cartDtoSender.setId(cartSender.getId());

        var cartDtoReceiver = new CartDto();
        cartDtoReceiver.setTitle("Ausgleichszahlung von " + formatUsername(user.getName()));
        cartDtoReceiver.setDescription("");
        cartDtoReceiver.setDatePurchased(datePurchased);
        cartDtoReceiver.setAmount((-1) * amount);
        cartDtoReceiver.setUserDto(new UserDto(member));
        cartDtoReceiver.setGroupId(group.getId());
        cartDtoReceiver.setCategoryDto(new CategoryDto(category));
        var cartReceiver = cartRepository.save(cartDtoMapper.cartDtoToEntity(cartDtoReceiver, category, member, group, groupMemberCount));
        cartDtoReceiver.setId(cartReceiver.getId());

        return Arrays.asList(cartDtoSender, cartDtoReceiver);
    }

    private String formatUsername(String name) {
        if (name != null && name.length() > 10) {
            return name.substring(0, 10) + "...";
        }
        return name;
    }

    private void createCategoryForSettlementPaymentIfNotExist(Group group) {
        if (categoryRepository.findCategoryByGroupAndName(group, SETTLEMENT_CATEGORY_NAME) == null) {
            categoryRepository.save(new Category(null, SETTLEMENT_CATEGORY_NAME, group, null));
        }
    }

    // ----- Wiederholende Einträge -----

//    @Scheduled(cron = "0 0 3 * * *") // täglich um 3 Uhr
    @Scheduled(fixedRate = 15000) // alle 15 sec
    @Transactional
    public void generateRecurringCarts() throws UserNotFoundException, GroupNotFoundException, CategoryNotFoundException {
        List<CartTemplate> templates = templateRepository.findByActiveTrue();
        var today = LocalDate.now();

        for (CartTemplate template : templates) {
            var nextDate = template.getNextExecutionDate();

            if (nextDate.equals(today)) {
                if (checkIfCartAlreadyExists(template) < 1) {
                    createCartFromTemplate(template, nextDate);
                }
                template.setNextExecutionDate(calculateNextDate(nextDate, template.getRecurrenceType()));
                templateRepository.save(template);
            }
        }
    }

    private int checkIfCartAlreadyExists(CartTemplate template) {
        return cartRepository.numberOfSimilarCarts(
                template.getTitle(),
                template.getDescription(),
                template.getCategoryId(),
                asDate(template.getNextExecutionDate()),
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

    private void createCartFromTemplate(CartTemplate template, LocalDate nextDate) throws UserNotFoundException, GroupNotFoundException, CategoryNotFoundException {
        var newCart = new Cart();
        var user = dataLoaderService.loadUser(template.getUserId());
        var group = dataLoaderService.loadGroup(template.getGroupId());
        var category = dataLoaderService.loadCategory(template.getCategoryId());
        var groupMemberCount = dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(Date.from(nextDate.atStartOfDay(ZoneId.systemDefault()).toInstant()), group.getId());
        newCart.setUser(user);
        newCart.setGroup(group);
        newCart.setTitle(template.getTitle());
        newCart.setDescription(template.getDescription());
        newCart.setAmount(template.getAmount());
        newCart.setAveragePerMember(template.getAmount() / groupMemberCount);
        newCart.setDescription(template.getDescription());
        newCart.setCategory(category);
        newCart.setDatePurchased(Date.from(nextDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        newCart.setTemplate(template);

        cartRepository.save(newCart);
    }

    private LocalDate calculateNextDate(LocalDate lastExecutionDate, RecurrenceType recurrenceType) {
        LocalDate next;
        LocalDate today = LocalDate.now();

        switch (recurrenceType) {
            case DAILY:
                next = lastExecutionDate.plusDays(1);
                while (!next.isAfter(today)) {
                    next = next.plusDays(1);
                }
                break;

            case WEEKLY:
                next = lastExecutionDate.plusWeeks(1);
                while (!next.isAfter(today)) {
                    next = next.plusWeeks(1);
                }
                break;

            case MONTHLY:
                next = lastExecutionDate.plusMonths(1);
                while (!next.isAfter(today)) {
                    next = next.plusMonths(1);
                }
                break;

            case YEARLY:
                next = lastExecutionDate.plusYears(1);
                while (!next.isAfter(today)) {
                    next = next.plusYears(1);
                }
                break;

            default:
                throw new IllegalArgumentException("Unsupported recurrence type: " + recurrenceType);
        }

        return next;
    }

    private void deactivateCartTemplate(Cart cart) {
        cart.getTemplate().setActive(false);
        cart.getTemplate().setEndDate(LocalDate.now());
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
        newTemplate.setStartDate(LocalDate.now());
        newTemplate.setNextExecutionDate(
                calculateNextDate(
                        cartDto.getDatePurchased().toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate(),
                        recurrenceType
                )
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

        RecurrenceType newType = dto.getRecurrenceType() != null ? dto.getRecurrenceType() : RecurrenceType.NONE;

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

        if (dto.getRecurrenceType() != RecurrenceType.NONE) {
            createNewCartTemplate(dto, user, group, cart, dto.getRecurrenceType());
        }
    }

    private void deactivateTemplate(CartTemplate template) {
        template.setActive(false);
        template.setEndDate(LocalDate.now());
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
        RecurrenceType newType = dto.getRecurrenceType() != null ? dto.getRecurrenceType() : RecurrenceType.NONE;

        if (tpl == null) {
            return newType != RecurrenceType.NONE;
        }

        if (!Objects.equals(tpl.getRecurrenceType(), newType)) return true;
        if (!Objects.equals(tpl.getAmount(), dto.getAmount())) return true;
        if (!Objects.equals(tpl.getTitle(), dto.getTitle())) return true;
        if (!Objects.equals(tpl.getDescription(), dto.getDescription())) return true;
        if (!Objects.equals(tpl.getCategoryId(), dto.getCategoryDto().getId())) return true;
        if (!Objects.equals(tpl.getNextExecutionDate(), calculateNextDate(asLocalDate(dto.getDatePurchased()), dto.getRecurrenceType()))) return true;

        return false;
    }

    private boolean hasRecurrenceTypeChanged(CartDto dto, Cart cart) {
        RecurrenceType oldType = (cart.getTemplate() != null && cart.getTemplate().isActive())
                ? cart.getTemplate().getRecurrenceType()
                : RecurrenceType.NONE;
        RecurrenceType newType = dto.getRecurrenceType() != null ? dto.getRecurrenceType() : RecurrenceType.NONE;
        return oldType != newType;
    }

    private Date asDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private LocalDate asLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
