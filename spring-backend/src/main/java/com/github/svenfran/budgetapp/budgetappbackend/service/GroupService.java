package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.constants.SupportedCurrency;
import com.github.svenfran.budgetapp.budgetappbackend.dto.*;
import com.github.svenfran.budgetapp.budgetappbackend.entity.*;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.helper.Translator;
import com.github.svenfran.budgetapp.budgetappbackend.repository.*;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.GroupDtoMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GroupService {

    private final GroupRepository groupRepository;

    private final GroupDtoMapper groupDtoMapper;

    private final CartRepository cartRepository;

    private final CategoryRepository categoryRepository;

    private final ShoppingListRepository shoppingListRepository;

    private final ShoppingItemRepository shoppingItemRepository;

    private final ShoppingPurchaseRepository shoppingPurchaseRepository;

    private final SettlementRepository settlementRepository;

    private final GroupMembershipHistoryService groupMembershipHistoryService;

    private final GroupMembershipHistoryRepository gmhRepository;

    private final DataLoaderService dataLoaderService;

    private final VerificationService verificationService;

    private final NotificationService notificationService;

    private final CartTemplateRepository cartTemplateRepository;

    private final GroupCurrencyChangeRepository groupCurrencyChangeRepository;

    private final Translator translator;

    private final Clock clock;

    public GroupService(GroupRepository groupRepository, GroupDtoMapper groupDtoMapper, CartRepository cartRepository,
                        CategoryRepository categoryRepository, ShoppingListRepository shoppingListRepository,
                        ShoppingItemRepository shoppingItemRepository,
                        ShoppingPurchaseRepository shoppingPurchaseRepository,
                        SettlementRepository settlementRepository,
                        GroupMembershipHistoryService groupMembershipHistoryService,
                        GroupMembershipHistoryRepository gmhRepository, DataLoaderService dataLoaderService,
                        VerificationService verificationService, NotificationService notificationService,
                        CartTemplateRepository cartTemplateRepository,
                        GroupCurrencyChangeRepository groupCurrencyChangeRepository, Translator translator, Clock clock) {
        this.groupRepository = groupRepository;
        this.groupDtoMapper = groupDtoMapper;
        this.cartRepository = cartRepository;
        this.categoryRepository = categoryRepository;
        this.shoppingListRepository = shoppingListRepository;
        this.shoppingItemRepository = shoppingItemRepository;
        this.shoppingPurchaseRepository = shoppingPurchaseRepository;
        this.settlementRepository = settlementRepository;
        this.groupMembershipHistoryService = groupMembershipHistoryService;
        this.gmhRepository = gmhRepository;
        this.dataLoaderService = dataLoaderService;
        this.verificationService = verificationService;
        this.notificationService = notificationService;
        this.cartTemplateRepository = cartTemplateRepository;
        this.groupCurrencyChangeRepository = groupCurrencyChangeRepository;
        this.translator = translator;
        this.clock = clock;
    }


    public Stream<Group> getGroupsByMemberOrOwner() throws UserNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        var member = new HashSet<User>();
        member.add(user);
        var groupsMember = groupRepository.findGroupsByMembersInOrderById(member);
        var groupsOwner = groupRepository.findGroupsByOwnerOrderById(user);
        return Stream.concat(groupsOwner.stream(), groupsMember.stream()).sorted((a,b) -> (int) (a.getId() - b.getId()));
    }

    /** Wie getGroupsByMemberOrOwner, aber jede Gruppe genau einmal (der Owner kann zugleich in members stehen), nach ID sortiert. */
    public List<Group> getDistinctGroupsByMemberOrOwner() throws UserNotFoundException {
        return getGroupsByMemberOrOwner()
                .collect(Collectors.toMap(Group::getId, group -> group, (first, duplicate) -> first, TreeMap::new))
                .values().stream().toList();
    }

    public List<GroupSideNavDto> getGroupsForSideNav() throws UserNotFoundException {
        return getGroupsByMemberOrOwner().map(GroupSideNavDto::new).toList();
    }

    public List<GroupOverviewDto> getGroupsForGroupOverview() throws UserNotFoundException {
        var groups = getGroupsByMemberOrOwner().toList();
        if (groups.isEmpty()) return List.of();
        // eine Abfrage für alle Gruppen statt einer je Gruppe (DIV-77)
        var groupIdsWithBookings = cartRepository.findGroupIdsWithCarts(groups.stream().map(Group::getId).toList());
        return groups.stream()
                .map(group -> new GroupOverviewDto(group, groupIdsWithBookings.contains(group.getId())))
                .toList();
    }

    public GroupMembersDto getGroupMembers(Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(groupId);
        verificationService.verifyIsPartOfGroup(user, group);
        return new GroupMembersDto(group);
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupDto addGroup(GroupDto groupDto) throws UserNotFoundException, UnsupportedCurrencyException {
        var user = dataLoaderService.getAuthenticatedUser();
        var currency = SupportedCurrency.fromCode(groupDto.getCurrencyCode());
        var group = groupRepository.save(groupDtoMapper.GroupDtoToEntity(groupDto, user, currency));
        createDefaultCategories(group);
        groupMembershipHistoryService.startGroupMembershipForOwner(user, group);
        return new GroupDto(group);
    }

    /**
     * Name und Währung einer Gruppe ändern (nur Owner). Erst alle Prüfungen, dann die Änderungen: Ein abgelehnter Request
     * ändert nichts, auch nicht den Namen (rollbackFor, weil die fachlichen Exceptions checked sind).
     * <p>
     * Währungswechsel (DIV-77): keine Umrechnung, alle Beträge gelten danach in der neuen Währung. Hat die Gruppe Buchungen,
     * ist {@code confirmCurrencyChange == true} Pflicht (sonst 409). Fehlt {@code currencyCode}, bleibt die Währung (alte
     * Clients). Die Zeilensperre serialisiert gleichzeitige Änderungen, das Protokoll bleibt eine lückenlose Kette. Eine
     * Buchung, die ein Mitglied währenddessen anlegt, wird bewusst nicht gesperrt: Sie gilt wie alle bestehenden Beträge
     * in der neuen Währung (D3, akzeptiert).
     */
    @Transactional(rollbackFor = Exception.class)
    public GroupDto updateGroup(GroupDto groupDto) throws UserNotFoundException, GroupNotFoundException,
            NotOwnerOfGroupException, UnsupportedCurrencyException, CurrencyChangeConfirmationRequiredException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroupForUpdate(groupDto.getId());
        verificationService.verifyIsGroupOwner(user, group);
        var requestedCurrency = SupportedCurrency.fromCode(groupDto.getCurrencyCode());
        var oldCurrency = group.getCurrencyCode();
        var currencyChanged = requestedCurrency != null && requestedCurrency != oldCurrency;
        if (currencyChanged) {
            verificationService.verifyCurrencyChangeConfirmed(
                    cartRepository.existsByGroupId(group.getId()), groupDto.getConfirmCurrencyChange());
        }

        group.setName(groupDto.getName());
        if (currencyChanged) {
            // currency_code ist updatable = false: nur per Update-Query schreiben, Feld für die Antwort nachziehen
            groupRepository.updateCurrencyCode(group.getId(), requestedCurrency);
            group.setCurrencyCode(requestedCurrency);
            groupCurrencyChangeRepository.save(new GroupCurrencyChange(
                    group.getId(), oldCurrency, requestedCurrency, user.getId(), Date.from(clock.instant())));
        }
        var updatedGroup = new GroupDto(groupRepository.save(group));
        notificationService.sendGroupUpdateNotification(group.getId(), updatedGroup);
        return updatedGroup;
    }

    @Transactional
    public GroupMembersDto addMemberToGroup(AddGroupMemberDto addGroupMemberDto) throws GroupNotFoundException, UserNotFoundException, NotOwnerOfGroupException, MemberAlreadyExistsException, MemberEqualsOwnerException {
        var user = dataLoaderService.getAuthenticatedUser();
        var newMember = dataLoaderService.loadUserByEmail(addGroupMemberDto.getNewMemberEmail().trim());
        verificationService.verifyUserExists(newMember);
        var group = dataLoaderService.loadGroup(addGroupMemberDto.getId());
        verificationService.verifyIsGroupOwner(user, group);
        verificationService.verifyCurrentlyNoGroupMember(newMember, group);
        verificationService.verifyMemberNotGroupOwner(newMember, user);

        group.addMember(newMember);
        groupMembershipHistoryService.startGroupMembershipForMember(newMember, group);
        setIsDeletedForCart(group, newMember, false);

        calculateAveragePerMember(group);
        var groupMemberDto = new GroupMembersDto(groupRepository.save(group), new UserDto(newMember));
        notificationService.sendGroupMemberAddedNotification(
                group.getId(),
                groupMemberDto
        );
        return groupMemberDto;
    }

    @Transactional
    public GroupMembersDto removeMemberFromGroup(RemoveGroupMemberDto removeGroupMemberDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var removedMember = dataLoaderService.loadUser(removeGroupMemberDto.getMember().getId());
        var group = dataLoaderService.loadGroup(removeGroupMemberDto.getId());
        var history = gmhRepository.findByGroupIdAndMembershipEndIsNull(group.getId());
        verificationService.verifyIsOwnerOrMemberToRemove(user, removedMember, group);

        group.removeMember(removedMember);
        groupMembershipHistoryService.finishGroupMembership(removedMember, group);
        setIsDeletedForCart(group, removedMember, true);

        deactivateCartTemplatesForUserAndGroup(removeGroupMemberDto.getMember().getId(), group.getId());

        calculateAveragePerMember(group);
        var groupMemberDto = new GroupMembersDto(groupRepository.save(group), new UserDto(removedMember));
        notificationService.sendGroupMemberRemovedNotification(
                history,
                groupMemberDto
        );
        return groupMemberDto;
    }

    @Transactional
    public void deleteGroup(Long id) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(id);
        var history = gmhRepository.findByGroupIdAndMembershipEndIsNull(group.getId());
        verificationService.verifyIsGroupOwner(user, group);
        var groupMembershipToRemove = dataLoaderService.loadMembershipHistoryForGroup(group.getId());
        if (!group.getMembers().isEmpty()) group.removeAllMembers();
        if (!groupMembershipToRemove.isEmpty()) gmhRepository.deleteAll(groupMembershipToRemove);
        if (!group.getCarts().isEmpty()) cartRepository.deleteAll(group.getCarts());
        // nach den Carts: cart.settlement_id verweist auf settlement (DIV-74)
        settlementRepository.deleteByGroupId(group.getId());
        if (!group.getCategories().isEmpty()) categoryRepository.deleteAll(group.getCategories());
        if (!group.getShoppingLists().isEmpty()) {
            group.getShoppingLists().forEach(list -> shoppingItemRepository.deleteAll(list.getShoppingItems()));
            shoppingListRepository.deleteAll(group.getShoppingLists());
        }
        cartTemplateRepository.deleteAll(cartTemplateRepository.findByGroupId(group.getId()));
        shoppingPurchaseRepository.deleteByGroupId(group.getId());
        groupRepository.deleteById(id);
        notificationService.sendGroupDeletedNotification(history, new GroupDto(group));
    }

    @Transactional
    public void changeGroupOwner(ChangeGroupOwnerDto changeGroupOwnerDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(changeGroupOwnerDto.getGroupId());
        var newGroupOwner = dataLoaderService.loadUser(changeGroupOwnerDto.getNewOwner().getId());
        verificationService.verifyIsGroupOwner(user, group);

        group.removeMember(newGroupOwner);
        group.setOwner(newGroupOwner);
        group.addMember(user);
        groupMembershipHistoryService.changeGroupOwnerToMember(user, group);
        groupMembershipHistoryService.changeGroupMemberToOwner(newGroupOwner, group);
        groupRepository.save(group);
        notificationService.sendGroupOwnerChangedNotification(group.getId(), newGroupOwner);
    }

    private void createDefaultCategories(Group group) {
        var defaultCategories = new String[]{
                "categories.default.go_out",
                "categories.default.settlement",
                "categories.default.gifts",
                "categories.default.grocery",
                "categories.default.restaurant",
                "categories.default.home",
                "categories.default.misc"
        };

        for (String categoryName : defaultCategories) {
            var category = new Category();
            category.setName(translator.translate(categoryName));
            category.setGroup(group);
            // Settlement-Kategorie per Kennzeichen statt Name (DIV-74)
            category.setSettlement(SettlementService.SETTLEMENT_CATEGORY_KEY.equals(categoryName));
            categoryRepository.save(category);
        }
    }

    @Transactional
    public void createDefaultGroup(User user) {
        var groupDto = new GroupDto();
        groupDto.setName(translator.translate("group.default.name"));
        var group = groupRepository.save(groupDtoMapper.GroupDtoToEntity(groupDto, user, SupportedCurrency.DEFAULT));
        createDefaultCategories(group);
        groupMembershipHistoryService.startGroupMembershipForOwner(user, group);
    }

    public void setIsDeletedForCart(Group group, User member, boolean delete) {
        // DIV-74 (A11): Dieser Weg und calculateAveragePerMember schreiben Carts komplett zurück. Erst alle Settlements der
        // Gruppe sperren, dann Carts laden – sonst könnte ein gleichzeitig geändertes Settlement-Paar mit Altwerten
        // überschrieben werden. Alle Aufrufer rufen diese Methode vor calculateAveragePerMember auf.
        settlementRepository.lockAllByGroupId(group.getId());
        var cartsOfMember = cartRepository.findCartsByGroupAndUser(group, member);
        if (!cartsOfMember.isEmpty()) {
            cartsOfMember.forEach(cart -> cart.setDeleted(delete));
            cartRepository.saveAll(cartsOfMember);
        }
    }

    public void calculateAveragePerMember(@NonNull Group group) {
        var cartsOfGroup = group.getCarts();

        if (!cartsOfGroup.isEmpty()) {
            calculateCarts(cartsOfGroup);
        }
    }

    public void calculateAveragePerMemberForAllCarts() {
        var carts = cartRepository.findAll();
        calculateCarts(carts);
    }

    private void calculateCarts(Iterable<Cart> carts) {
        carts.forEach(cart -> {
            int memberCount = dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(
                    cart.getDatePurchased(),
                    cart.getGroup().getId()
            );

            if (memberCount > 0) {
                cart.setAveragePerMember(cart.getAmount() / memberCount);
            } else {
                cart.setAveragePerMember(0.0);
            }
        });

        cartRepository.saveAll(carts);
    }

    private void deactivateCartTemplatesForUserAndGroup(Long userId, Long groupId) {
        var activeTemplates = cartTemplateRepository.findByUserIdAndGroupIdAndActiveTrue(userId, groupId);
        for (CartTemplate template : activeTemplates) {
            template.setActive(false);
            template.setEndDate(LocalDate.now(clock));
        }
        cartTemplateRepository.saveAll(activeTemplates);
    }
}
