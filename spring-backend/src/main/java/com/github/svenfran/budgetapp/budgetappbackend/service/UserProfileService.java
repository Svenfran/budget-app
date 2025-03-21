package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum;
import com.github.svenfran.budgetapp.budgetappbackend.dto.PasswordChangeDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.repository.*;
import liquibase.repackaged.org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

@Service
public class UserProfileService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataLoaderService dataLoaderService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ShoppingListRepository shoppingListRepository;

    @Autowired
    private ShoppingItemRepository shoppingItemRepository;

    @Autowired
    private GroupMembershipHistoryService gmhService;

    @Autowired
    private CartService cartService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private EmailSenderService senderService;

    @Autowired
    private VerificationService verificationService;


    @Transactional
    public void deleteUserProfile(Long userId) throws UserNotFoundException, UserIsNotAuthenticatedUser {
        var userAuth = dataLoaderService.getAuthenticatedUser();
        var userDelete = dataLoaderService.loadUser(userId);
        verificationService.verifyIsAuthenticatedUser(userDelete, userAuth);

        userDelete.getGroupList().forEach(group -> {
            if (group.getOwner().equals(userDelete)) {
                deleteGroupAsOwner(group);
            } else if (group.getMembers().contains(userDelete)) {
                removeUserFromGroup(group, userDelete);
            }
        });

        anonymizeUser(userDelete);
    }

    private void deleteGroupAsOwner(Group group) {
        dataLoaderService.loadMembershipHistoryForGroup(group.getId())
                .forEach(gmh -> gmh.setGroupId(null));

        gmhService.deleteGroupMembershipHistoryWhereGroupIdIsNull();

        group.removeAllMembers();
        cartRepository.deleteAll(group.getCarts());
        categoryRepository.deleteAll(group.getCategories());

        group.getShoppingLists().forEach(list ->
                shoppingItemRepository.deleteAll(list.getShoppingItems())
        );
        shoppingListRepository.deleteAll(group.getShoppingLists());

        groupRepository.deleteById(group.getId());
    }

    private void removeUserFromGroup(Group group, User user) {
        group.removeMember(user);
        gmhService.finishGroupMembership(user, group);
        groupService.setIsDeletedForCart(group, user, true);
        groupService.calculateAveragePerMember(group);
    }

    private void anonymizeUser(User user) {
        user.setName(UserEnum.USER_DELETED.getName());
        user.setEmail("deleted_" + user.getId() + "@example.com");
        user.setPassword(passwordEncoder.encode(RandomStringUtils.randomAlphanumeric(8)));
        userRepository.save(user);
    }

    public UserDto changeUserName(UserDto userDto) throws UserNotFoundException, UserIsNotAuthenticatedUser, UserNameAlreadyExistsException {
        var userAuth = dataLoaderService.getAuthenticatedUser();
        var userChange = dataLoaderService.loadUser(userDto.getId());
        verificationService.verifyIsAuthenticatedUser(userChange, userAuth);
        verificationService.verifyUserNameNotExists(userDto.getUserName());
        userChange.setName(userDto.getUserName());
        return new UserDto(userRepository.save(userChange), userChange.getEmail());
    }

    public UserDto changeUserEmail(UserDto userDto, BindingResult bindingResult) throws UserNotFoundException, UserIsNotAuthenticatedUser, UserAlreadyExistException, InvalidEmailException {
        var userAuth = dataLoaderService.getAuthenticatedUser();
        var userChange = dataLoaderService.loadUser(userDto.getId());
        verificationService.verifyIsAuthenticatedUser(userChange, userAuth);
        verificationService.verifyEmailIsValid(bindingResult);
        verificationService.verifyEmailNotExists(userDto.getUserEmail());
        userChange.setEmail(userDto.getUserEmail());
        return new UserDto(userRepository.save(userChange), userDto.getUserEmail());
    }

    public void changePassword(PasswordChangeDto passwordChangeDto) throws UserNotFoundException, UserIsNotAuthenticatedUser, WrongPasswordException {
        var userAuth = dataLoaderService.getAuthenticatedUser();
        var userPwChange = dataLoaderService.loadUser(passwordChangeDto.getUserId());
        verificationService.verifyIsAuthenticatedUser(userPwChange, userAuth);
        verificationService.verifyIsCorrectPassword(userPwChange, passwordChangeDto.getOldPassword());
        userPwChange.setPassword(passwordEncoder.encode(passwordChangeDto.getNewPassword()));
        userRepository.save(userPwChange);
    }

    public void passwordReset(String email, BindingResult bindingResult) throws UserNotFoundException, InvalidEmailException {
        verificationService.verifyEmailIsValid(bindingResult);
        var user = dataLoaderService.loadUserByEmail(email);
        var generatedPassword = RandomStringUtils.randomAlphanumeric(8);
        var subject = "Passwort zurücksetzen";
        var body = "Hallo " + user.getName() + "," +
                "\n\ndein temporäres Passwort lautet: " + generatedPassword +
                "\n\nBitte melde dich an und ändere dein Passwort." +
                "\n\nBeste Grüße!" ;
        user.setPassword(passwordEncoder.encode(generatedPassword));
        userRepository.save(user);
        senderService.sendEmail(email, body, subject);
    }
}
