package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.constants.SessionRevokeReason;
import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum;
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.PasswordChangeDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.CartTemplate;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.helper.EmailUtils;
import com.github.svenfran.budgetapp.budgetappbackend.helper.Translator;
import com.github.svenfran.budgetapp.budgetappbackend.repository.*;
import liquibase.repackaged.org.apache.commons.lang3.RandomStringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;

/**
 * Profilaktionen. Passwortänderung, Passwort-Reset, E-Mail-Änderung und Konto löschen beenden alle Sessions des
 * Nutzers (DIV-65, E4): Sie sperren zuerst die Nutzerzeile (erster Zugriff auf die Entity, feste Reihenfolge Nutzer
 * vor Sessions) und widerrufen bzw. löschen die Sessions in derselben Transaktion. Ein paralleler Login wartet auf die
 * Sperre und prüft danach das neue Passwort bzw. findet die alte E-Mail nicht mehr - oder er war vorher fertig, dann
 * trifft der Widerruf auch seine Session.
 */
@Service
public class UserProfileService {

    private final UserRepository userRepository;

    private final DataLoaderService dataLoaderService;

    private final GroupService groupService;

    private final CartRepository cartRepository;

    private final GroupRepository groupRepository;

    private final CategoryRepository categoryRepository;

    private final ShoppingListRepository shoppingListRepository;

    private final ShoppingItemRepository shoppingItemRepository;

    private final ShoppingPurchaseRepository shoppingPurchaseRepository;

    private final SettlementRepository settlementRepository;

    private final GroupMembershipHistoryService gmhService;

    private final PasswordEncoder passwordEncoder;

    private final EmailSenderService senderService;

    private final VerificationService verificationService;

    private final CartTemplateRepository cartTemplateRepository;

    private final NotificationService notificationService;

    private final GroupMembershipHistoryRepository gmhRepository;

    private final Translator translator;

    private final Clock clock;

    private final AuthSessionService authSessionService;

    public UserProfileService(UserRepository userRepository, DataLoaderService dataLoaderService,
                              GroupService groupService, CartRepository cartRepository, GroupRepository groupRepository,
                              CategoryRepository categoryRepository, ShoppingListRepository shoppingListRepository,
                              ShoppingItemRepository shoppingItemRepository,
                              ShoppingPurchaseRepository shoppingPurchaseRepository, SettlementRepository settlementRepository,
                              GroupMembershipHistoryService gmhService,
                              PasswordEncoder passwordEncoder, EmailSenderService senderService, VerificationService verificationService,
                              CartTemplateRepository cartTemplateRepository, NotificationService notificationService,
                              GroupMembershipHistoryRepository gmhRepository, Translator translator,
                              Clock clock, AuthSessionService authSessionService) {
        this.userRepository = userRepository;
        this.dataLoaderService = dataLoaderService;
        this.groupService = groupService;
        this.cartRepository = cartRepository;
        this.groupRepository = groupRepository;
        this.categoryRepository = categoryRepository;
        this.shoppingListRepository = shoppingListRepository;
        this.shoppingItemRepository = shoppingItemRepository;
        this.shoppingPurchaseRepository = shoppingPurchaseRepository;
        this.settlementRepository = settlementRepository;
        this.gmhService = gmhService;
        this.passwordEncoder = passwordEncoder;
        this.senderService = senderService;
        this.verificationService = verificationService;
        this.cartTemplateRepository = cartTemplateRepository;
        this.notificationService = notificationService;
        this.gmhRepository = gmhRepository;
        this.translator = translator;
        this.clock = clock;
        this.authSessionService = authSessionService;
    }

    @Transactional
    public void deleteUserProfile(Long userId) throws UserNotFoundException, UserIsNotAuthenticatedUser {
        var userDelete = lockUser(userId);
        var userAuth = dataLoaderService.getAuthenticatedUser();
        verificationService.verifyIsAuthenticatedUser(userDelete, userAuth);

        var groupsOwner = groupRepository.findGroupsByOwnerOrderById(userDelete);
        for (Group group : groupsOwner) {
            deleteGroupAsOwner(group);
        }

        var members = new HashSet<User>();
        members.add(userDelete);
        var groupsMember = groupRepository.findGroupsByMembersInOrderById(members);
        for (Group group : groupsMember) {
            removeUserFromGroup(group, userDelete);
        }

        authSessionService.deleteAllForUser(userDelete);
        anonymizeUser(userDelete);
    }

    /** Sperrt die Nutzerzeile (DIV-65); muss der erste Zugriff auf die Entity in der Transaktion sein. */
    private User lockUser(Long userId) throws UserNotFoundException {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(String.format("User with Id %s not found", userId)));
    }

    private void deleteGroupAsOwner(Group group) throws UserNotFoundException {
        var history = gmhRepository.findByGroupIdAndMembershipEndIsNull(group.getId());

        dataLoaderService.loadMembershipHistoryForGroup(group.getId())
                .forEach(gmh -> gmh.setGroupId(null));

        gmhService.deleteGroupMembershipHistoryWhereGroupIdIsNull();

        group.removeAllMembers();
        cartRepository.deleteAll(group.getCarts());
        // nach den Carts: cart.settlement_id verweist auf settlement (DIV-74)
        settlementRepository.deleteByGroupId(group.getId());
        categoryRepository.deleteAll(group.getCategories());
        cartTemplateRepository.deleteAll(cartTemplateRepository.findByGroupId(group.getId()));

        group.getShoppingLists().forEach(list ->
                shoppingItemRepository.deleteAll(list.getShoppingItems())
        );

        shoppingListRepository.deleteAll(group.getShoppingLists());
        shoppingPurchaseRepository.deleteByGroupId(group.getId());
        groupRepository.deleteById(group.getId());

        notificationService.sendGroupDeletedNotification(history, new GroupDto(group));
    }

    private void removeUserFromGroup(Group group, User user) {
        group.removeMember(user);
        gmhService.finishGroupMembership(user, group);
        groupService.setIsDeletedForCart(group, user, true);
        var templates = cartTemplateRepository.findByUserIdAndGroupIdAndActiveTrue(user.getId(), group.getId());
        for (CartTemplate cartTemplate : templates) {
            cartTemplate.setEndDate(LocalDate.now(clock));
            cartTemplate.setActive(false);
            cartTemplateRepository.save(cartTemplate);
        }
    }

    private void anonymizeUser(User user) {
        user.setName(UserEnum.USER_DELETED.getName());
        user.setEmail("deleted_" + user.getId() + "@example.com");
        user.setPassword(passwordEncoder.encode(RandomStringUtils.randomAlphanumeric(8)));
        userRepository.save(user);
    }

    public UserDto changeUserName(UserDto userDto) throws UserNotFoundException, UserIsNotAuthenticatedUser, UserNameAlreadyExistsException, UserNameNotAllowedException {
        var userAuth = dataLoaderService.getAuthenticatedUser();
        var userChange = dataLoaderService.loadUser(userDto.getId());
        verificationService.verifyIsAuthenticatedUser(userChange, userAuth);
        // Umgebende Leerzeichen (z. B. von der Tastatur-Autovervollständigung) nicht mitspeichern
        var userName = userDto.getUserName() == null ? null : userDto.getUserName().trim();
        verificationService.verifyUserNameIsAllowed(userName);
        verificationService.verifyUserNameNotExists(userName);
        userChange.setName(userName);
        return new UserDto(userRepository.save(userChange), userChange.getEmail());
    }

    @Transactional
    public UserDto changeUserEmail(UserDto userDto, BindingResult bindingResult) throws UserNotFoundException, UserIsNotAuthenticatedUser, UserAlreadyExistException, InvalidEmailException {
        var userChange = lockUser(userDto.getId());
        var userAuth = dataLoaderService.getAuthenticatedUser();
        verificationService.verifyIsAuthenticatedUser(userChange, userAuth);
        verificationService.verifyEmailIsValid(bindingResult, "userEmail");
        // Same address as before after normalization (e.g. only case differs): nothing to change
        if (EmailUtils.normalize(userDto.getUserEmail()).equals(userChange.getEmail())) {
            return new UserDto(userChange, userChange.getEmail());
        }
        verificationService.verifyEmailNotExists(userDto.getUserEmail());
        userChange.setEmail(userDto.getUserEmail());
        var saved = userRepository.save(userChange);
        authSessionService.revokeAllSessions(saved, SessionRevokeReason.EMAIL_CHANGED);
        return new UserDto(saved, saved.getEmail());
    }

    @Transactional
    public void changePassword(PasswordChangeDto passwordChangeDto) throws UserNotFoundException, UserIsNotAuthenticatedUser, WrongPasswordException {
        var userPwChange = lockUser(passwordChangeDto.getUserId());
        var userAuth = dataLoaderService.getAuthenticatedUser();
        verificationService.verifyIsAuthenticatedUser(userPwChange, userAuth);
        verificationService.verifyIsCorrectPassword(userPwChange, passwordChangeDto.getOldPassword());
        userPwChange.setPassword(passwordEncoder.encode(passwordChangeDto.getNewPassword()));
        userRepository.save(userPwChange);
        authSessionService.revokeAllSessions(userPwChange, SessionRevokeReason.PASSWORD_CHANGED);
    }

    /**
     * Setzt ein Zufallspasswort und mailt es (öffentlich). Beendet alle Sessions des Nutzers (DIV-65, E4).
     * Scheitert der Mailversand (Brevo wirft eine geprüfte Exception), wird alles zurückgerollt: das alte Passwort
     * gilt weiter, die Sessions bleiben aktiv – sonst wäre der Nutzer ohne Mail ausgesperrt.
     */
    @Transactional(rollbackFor = Exception.class)
    public void passwordReset(String email, BindingResult bindingResult) throws Exception {
        verificationService.verifyEmailIsValid(bindingResult, "email");
        var user = userRepository.findByEmailForUpdate(EmailUtils.normalize(email))
                .orElseThrow(() -> new UserNotFoundException(String.format("User with email %s not found", email)));
        var generatedPassword = RandomStringUtils.randomNumeric(6);
        var subject = translator.translate("email.reset.password.subject");

        var body = """
        <html>
          <body style="font-family: Arial, sans-serif; color: #333;">
            <p>%s</p>
            <p>%s</p>
            <p>%s</p>
            <p>%s</p>
          </body>
        </html>
        """.formatted(
                translator.translate("email.reset.password.greeting", user.getName()),
                translator.translate("email.reset.password.passwordLine", generatedPassword),
                translator.translate("email.reset.password.instruction"),
                translator.translate("email.reset.password.footer")
        );

        user.setPassword(passwordEncoder.encode(generatedPassword));
        userRepository.save(user);
        authSessionService.revokeAllSessions(user, SessionRevokeReason.PASSWORD_RESET);
        senderService.sendEmail(user.getEmail(), body, subject);
    }
}
