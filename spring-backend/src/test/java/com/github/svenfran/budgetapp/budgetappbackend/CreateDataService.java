package com.github.svenfran.budgetapp.budgetappbackend;

import com.github.svenfran.budgetapp.budgetappbackend.constants.TokenType;
import com.github.svenfran.budgetapp.budgetappbackend.constants.TypeEnum;
import com.github.svenfran.budgetapp.budgetappbackend.entity.*;
import com.github.svenfran.budgetapp.budgetappbackend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class CreateDataService {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupMembershipHistoryRepository gmhRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY);

    public User createUser(String userName) {
        var user = new User();
        user.setName(userName);
        user.setPassword(passwordEncoder.encode(String.format("!!%s!!", userName)));
        user.setEmail(userName + "@tester.de");
        return userRepository.save(user);
    }

    public Group createGroup(String groupName, User owner) {
        var group = new Group();
        group.setName(groupName);
        group.setOwner(owner);
        return groupRepository.save(group);
    }

    public Cart createCart(
            String cartTitle, Double amount, Group group, User user, Category category,
            boolean isDeleted, String datePurchasedString, int memberCount) throws ParseException {
        var cart = new Cart();
        cart.setTitle(cartTitle);
        cart.setGroup(group);
        cart.setUser(user);
        cart.setCategory(category);
        cart.setAmount(amount);
        if (datePurchasedString.equals("today")) {
            cart.setDatePurchased(new Date());
        } else {
            cart.setDatePurchased(formatter.parse(datePurchasedString));
        }
        cart.setDeleted(isDeleted);
        cart.setAveragePerMember(amount/memberCount);
        return cartRepository.save(cart);
    }

    public Category createCategory(String categoryName, Group group) {
        var category = new Category();
        category.setName(categoryName);
        category.setGroup(group);
        return categoryRepository.save(category);
    }

    public Set<Cart> createListOfCartsForGroupAndUser
            (int numberOfCarts, Double amount, Group group, User user,
             Category category, boolean isDeleted, String datePurchasedString, int memberCount
            ) throws ParseException {
        Set<Cart> cartList = new HashSet<>();
        for (int i = 0; i < numberOfCarts ; i++) {
            var cart = createCart("Cart_" + (i + 1), amount, group, user, category, isDeleted, datePurchasedString, memberCount);
            cartList.add(cart);
        }
        return cartList;
    }

    public Token createTokenForUser(String jwt, User user, boolean isRevoked, boolean isExpired) {
        var token = new Token();
        token.setUser(user);
        token.setTokenType(TokenType.BEARER);
        token.setToken(jwt);
        token.setRevoked(isRevoked);
        token.setExpired(isExpired);
        return tokenRepository.save(token);
    }

    public GroupMembershipHistory createGmh(Long groupId, Long userId, TypeEnum type, String msStart, String msEnd) throws ParseException {
        var gmh = new GroupMembershipHistory();
        gmh.setGroupId(groupId);
        gmh.setUserId(userId);
        gmh.setType(type);
        gmh.setMembershipStart(formatter.parse(msStart));
        if (msEnd.equals("null")) {
            gmh.setMembershipEnd(null);
        } else {
            gmh.setMembershipEnd(formatter.parse(msEnd));
        }
        return gmhRepository.save(gmh);
    }
}
