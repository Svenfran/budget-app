package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.*;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.helper.EmailUtils;
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils.toLocalDate;

@Service
public class VerificationService {

    private final PasswordEncoder passwordEncoder;

    private final UserRepository userRepository;

    public VerificationService(PasswordEncoder passwordEncoder, UserRepository userRepository) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
    }

    public void verifyIsAuthenticatedUser(User user, User authUser) throws UserIsNotAuthenticatedUser {
        if (!user.equals(authUser)) {
            throw new UserIsNotAuthenticatedUser("User to edit or delete is not authenticated user");
        }
    }

    public void verifyIsCorrectPassword(User user, String password) throws WrongPasswordException {
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new WrongPasswordException("Incorrect password");
        }
    }

    public void verifyEmailNotExists(String email) throws UserAlreadyExistException {
        if (emailExists(email)) {
            throw new UserAlreadyExistException(String.format("User with email %s already exists", email));
        }
    }

    // only errors of the given e-mail field count: other constraints of the DTO (e.g. deviceId) have their own checks
    public void verifyEmailIsValid(BindingResult bindingResult, String emailField) throws InvalidEmailException {
        if (bindingResult.hasFieldErrors(emailField)) {
            throw new InvalidEmailException("Invalid Email");
        }
    }

    public void verifyDeviceIdIsPresent(BindingResult bindingResult) throws MissingDeviceIdException {
        if (bindingResult.hasFieldErrors("deviceId")) {
            throw new MissingDeviceIdException("DeviceId is required");
        }
    }

    public void verifyUserNameNotExists(String userName) throws UserNameAlreadyExistsException {
        if (userNameExists(userName)) {
            throw new UserNameAlreadyExistsException(String.format("User with name %s already exists", userName));
        }
    }

    public void verifyUserNameIsAllowed(String userName) throws UserNameNotAllowedException {
        if (userName.contains(UserEnum.USER_DELETED.getName()) || userName.contains(UserEnum.USER_REMOVED.getName())) {
            throw new UserNameNotAllowedException("User name is not allowed");
        }
    }

    public void verifyIsPartOfGroup(User user, Group group) throws NotOwnerOrMemberOfGroupException {
        if (!(group.getOwner().equals(user) || group.getMembers().contains(user))) {
            throw new NotOwnerOrMemberOfGroupException("User with ID " + user.getId() + " is either a member nor the owner of the group");
        }
    }

    public void verifyIsOwnerOfCart(User user, Cart cart) throws NotOwnerOfCartException {
        if (!cart.getUser().equals(user)) {
            throw new NotOwnerOfCartException("User with ID " + user.getId() + " is not the owner of the cart");
        }
    }

    public void verifyCategoryIsPartOfGroup(Category category, Group group) throws CategoryBelongsNotToGroupException {
        if (!group.getId().equals(category.getGroup().getId())) {
            throw new CategoryBelongsNotToGroupException("Category with Id " + category.getId() + " does not belong to group with Id " + group.getId());
        }
    }

    public void verifyCategoryNotInUse(Category category) throws CategoryIsUsedByCartException {
        if (!category.getCarts().isEmpty()) {
            throw new CategoryIsUsedByCartException("Category with Id " + category.getId() + " is used by carts");
        }
    }

    public void verifyIsGroupOwner(User user, Group group) throws NotOwnerOfGroupException {
        if (!group.getOwner().equals(user)) {
            throw new NotOwnerOfGroupException("User with Id " + user.getId() + " is not the owner of the Group");
        }
    }

    public void verifyUserExists(User user) throws UserNotFoundException {
        if (user == null) throw new UserNotFoundException("User not found");
    }

    public void verifyCurrentlyNoGroupMember(User user, Group group) throws MemberAlreadyExistsException {
        if (group.getMembers().contains(user)) {
            throw new MemberAlreadyExistsException("Member already exists");
        }
    }

    public void verifyMemberNotGroupOwner(User newMember, User user) throws MemberEqualsOwnerException {
        if (newMember.equals(user)) {
            throw new MemberEqualsOwnerException("New member equals group owner");
        }
    }

    public void verifyIsOwnerOrMemberToRemove(User user, User removedMember, Group group) throws NotOwnerOfGroupException {
        if (!(user.equals(group.getOwner()) || user.equals(removedMember))) {
            throw new NotOwnerOfGroupException("User with Id " + user.getId() + " not allowed to remove other members from the group");
        }
    }

    public void verifyShoppingListIsPartOfGroup(ShoppingList shoppingList, Group group) throws ShoppingListDoesNotBelongToGroupException {
        if (!shoppingList.getGroup().equals(group)) {
            throw new ShoppingListDoesNotBelongToGroupException("Shoppinglist with Id " + shoppingList.getId() + " does not belong to group with Id " + group.getId());
        }
    }

    public void verifyShoppingItemIsPartOfShoppingList(ShoppingList shoppingList, ShoppingItem shoppingItem) throws ShoppingItemDoesNotBelongToShoppingListException {
        if (!shoppingList.getId().equals(shoppingItem.getShoppingList().getId())) {
            throw new ShoppingItemDoesNotBelongToShoppingListException("Shoppingitem with Id " + shoppingItem.getId() + " does not belong to shopping list with Id " + shoppingList.getId());
        }
    }

    public void verifyAllShoppingItemsBelongToSameShoppingListAndGroup(List<AddEditShoppingItemDto> shoppingItems) {
        if (shoppingItems.stream()
                .map(shoppingItem -> Map.entry(shoppingItem.getGroupId(), shoppingItem.getShoppingListId()))
                .distinct()
                .count() > 1) {
            throw new IllegalArgumentException("One of the shopping items does not belong to the shopping list or group ");
        }
    }

    public void verifyDatePurchasedWithinMembershipPeriod (List<GroupMembershipHistory> gmh, Date datePurchased) throws DatePurchasedNotWithinMembershipPeriodException {
        for (var timePeriod : gmh) {
            var startDate = toLocalDate(timePeriod.getMembershipStart());
            var endDate = timePeriod.getMembershipEnd() != null ? toLocalDate(timePeriod.getMembershipEnd()) : null;

            var isAfterStart = !toLocalDate(datePurchased).isBefore(startDate);
            var isBeforeEnd = (endDate == null) || !toLocalDate(datePurchased).isAfter(endDate);

            if (isAfterStart && isBeforeEnd) {
                return;
            }
        } throw new DatePurchasedNotWithinMembershipPeriodException("Date purchased is not within membership period");
    }

    /**
     * Request-Struktur beim Anlegen einer Ausgleichszahlung (DIV-74). Wird vor jedem Loader aufgerufen, der eine Id aus
     * dem Request verwendet, damit fehlende Felder als 400 statt als NPE/500 enden.
     */
    public void verifyValidSettlementPaymentForCreate(SettlementPaymentDto dto) throws InvalidSettlementPaymentException {
        if (dto == null) throw new InvalidSettlementPaymentException("Settlement payment is required");
        requireSettlementField(dto.getGroupId(), "groupId");
        requireSettlementField(dto.getMember(), "member");
        requireSettlementField(dto.getMember().getId(), "member.id");
        if (dto.getPayer() != null) {
            requireSettlementField(dto.getPayer().getId(), "payer.id");
        }
        requireSettlementField(dto.getDatePurchased(), "datePurchased");
        verifyValidSettlementAmount(dto.getAmount());
    }

    /** Anlegen einer Ausgleichszahlung nur, wenn der angemeldete Nutzer Zahler oder Empfänger ist (DIV-74). */
    public void verifyIsPartyOfPayment(User user, User payer, User receiver) throws NotPartyOfSettlementException {
        if (!(user.getId().equals(payer.getId()) || user.getId().equals(receiver.getId()))) {
            throw new NotPartyOfSettlementException("User with ID " + user.getId()
                    + " is neither payer nor receiver of the settlement payment");
        }
    }

    /**
     * Request-Struktur beim Bearbeiten einer Ausgleichszahlung (DIV-74): id, Empfänger, Betrag, Datum. groupId wird nicht
     * geprüft, maßgeblich ist die Gruppe des Settlements. Vor jedem Loader aufrufen.
     */
    public void verifyValidSettlementPaymentForUpdate(SettlementPaymentDto dto) throws InvalidSettlementPaymentException {
        if (dto == null) throw new InvalidSettlementPaymentException("Settlement payment is required");
        requireSettlementField(dto.getId(), "id");
        requireSettlementField(dto.getMember(), "member");
        requireSettlementField(dto.getMember().getId(), "member.id");
        requireSettlementField(dto.getDatePurchased(), "datePurchased");
        verifyValidSettlementAmount(dto.getAmount());
    }

    /** Den Empfänger darf nur der Zahler ändern, sonst könnte sich der Empfänger selbst herausnehmen (DIV-74). */
    public void verifyMayChangeReceiver(User user, Settlement settlement, User newReceiver) throws ReceiverChangeNotAllowedException {
        if (!newReceiver.getId().equals(settlement.getReceiver().getId()) && !user.getId().equals(settlement.getPayer().getId())) {
            throw new ReceiverChangeNotAllowedException("Only the payer may change the receiver of settlement " + settlement.getId());
        }
    }

    /** Betrag einer Ausgleichszahlung: Pflicht, größer als 0 und höchstens zwei Nachkommastellen (Cent). */
    public void verifyValidSettlementAmount(Double amount) throws InvalidSettlementPaymentException {
        requireSettlementField(amount, "amount");
        if (amount.isNaN() || amount.isInfinite() || amount <= 0
                || BigDecimal.valueOf(amount).stripTrailingZeros().scale() > 2) {
            throw new InvalidSettlementPaymentException("Settlement amount must be greater than 0 with at most 2 decimal places");
        }
    }

    /**
     * Ein Währungswechsel einer Gruppe mit Buchungen braucht die ausdrückliche Bestätigung (DIV-77). Nur aufrufen, wenn
     * sich die Währung tatsächlich ändert.
     */
    public void verifyCurrencyChangeConfirmed(boolean hasBookings, Boolean confirmed) throws CurrencyChangeConfirmationRequiredException {
        if (hasBookings && !Boolean.TRUE.equals(confirmed)) {
            throw new CurrencyChangeConfirmationRequiredException(
                    "Changing the currency of a group with bookings requires confirmation");
        }
    }

    /**
     * Cart-Betrag mit höchstens zwei Nachkommastellen (DIV-77, gleiche Prüfung wie {@link #verifyValidSettlementAmount}).
     * {@code null} und Vorzeichen verhalten sich wie bisher (keine Pflicht hier, negative Beträge erlaubt). Das Frontend
     * rundet vor dem Senden; abgelehnt wird, was ungerundet ankommt (alte Clients, direkte API-Aufrufe).
     */
    public void verifyCartAmountHasAtMostTwoDecimals(Double amount) throws InvalidAmountException {
        if (amount == null || amount.isNaN() || amount.isInfinite()) return;
        if (BigDecimal.valueOf(amount).stripTrailingZeros().scale() > 2) {
            throw new InvalidAmountException("Amount must have at most 2 decimal places");
        }
    }

    public void verifyPayerIsNotReceiver(User payer, User receiver) throws InvalidSettlementPaymentException {
        if (payer.getId().equals(receiver.getId())) {
            throw new InvalidSettlementPaymentException("Payer and receiver of a settlement payment must differ");
        }
    }

    /** Settlement-Carts ändern sich nur über die Settlement-Endpunkte (DIV-74). */
    public void verifyCartIsNotSettlement(Cart cart) throws SettlementCartNotEditableException {
        if (cart.getSettlement() != null) {
            throw new SettlementCartNotEditableException("Cart with Id " + cart.getId()
                    + " belongs to a settlement payment and can only be changed via the settlement endpoints");
        }
    }

    /** Die Settlement-Kategorie ist für normale Einkäufe gesperrt (DIV-74). */
    public void verifyCategoryIsNotSettlement(Category category) throws SettlementCategoryNotAllowedException {
        if (category.isSettlement()) {
            throw new SettlementCategoryNotAllowedException("The settlement category with Id " + category.getId()
                    + " cannot be used for regular carts");
        }
    }

    /** Ändern und Löschen einer Ausgleichszahlung dürfen nur Zahler und Empfänger (DIV-74). */
    public void verifyIsPartyOfSettlement(User user, Settlement settlement) throws NotPartyOfSettlementException {
        if (!(user.getId().equals(settlement.getPayer().getId()) || user.getId().equals(settlement.getReceiver().getId()))) {
            throw new NotPartyOfSettlementException("User with ID " + user.getId()
                    + " is neither payer nor receiver of settlement " + settlement.getId());
        }
    }

    private static void requireSettlementField(Object value, String field) throws InvalidSettlementPaymentException {
        if (value == null) {
            throw new InvalidSettlementPaymentException("Settlement payment field '" + field + "' is required");
        }
    }

    private boolean emailExists(String email) {
        return userRepository.findByEmail(EmailUtils.normalize(email)).isPresent();
    }

    private boolean userNameExists(String userNAme) {
        return userRepository.findByName(userNAme).isPresent();
    }

}
