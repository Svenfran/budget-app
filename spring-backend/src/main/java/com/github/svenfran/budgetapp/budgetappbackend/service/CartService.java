package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.helper.ExcelWriter;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CategoryRepository;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.CartDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.List;

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

    private static final String SETTLEMENT_CATEGORY_NAME = "Ausgleichszahlung";


    public List<CartDto> getCartsByGroupId(Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(groupId);
        verifyIsPartOfGroup(user, group);
        var cartList = dataLoaderService.loadCartListForGroup(groupId);
        return cartList.stream().map(CartDto::new).toList();
    }

    public CartDto getCartById(Long id) throws CartNotFoundException, UserNotFoundException, NotOwnerOrMemberOfGroupException, GroupNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        var cart = dataLoaderService.loadCart(id);
        var group = dataLoaderService.loadGroup(cart.getGroup().getId());
        verifyIsPartOfGroup(user, group);
        return new CartDto(cart);
    }

    public CartDto addCart(@Validated CartDto cartDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, CategoryNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        var category = dataLoaderService.loadCategory(cartDto.getCategoryDto().getId());
        var group = dataLoaderService.loadGroup(cartDto.getGroupId());
        verifyIsPartOfGroup(user, group);
        var groupMemberCount = dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(cartDto.getDatePurchased(), group.getId());
        return new CartDto(cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDto, category, user, group, groupMemberCount)));
    }

    public CartDto updateCart(@Validated CartDto cartDto) throws UserNotFoundException, GroupNotFoundException, CartNotFoundException, NotOwnerOfCartException, NotOwnerOrMemberOfGroupException, CategoryNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        var cart = dataLoaderService.loadCart(cartDto.getId());
        var group = dataLoaderService.loadGroup(cartDto.getGroupId());
        verifyIsPartOfGroup(user, group);
        verifyIsOwnerOfCart(user, cart);
        var category = dataLoaderService.loadCategory(cartDto.getCategoryDto().getId());
        var groupMemberCount = dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(cartDto.getDatePurchased(), group.getId());
        return new CartDto(cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDto, category, user, group, groupMemberCount)));
    }

    public void deleteCart(Long id) throws UserNotFoundException, CartNotFoundException, GroupNotFoundException, NotOwnerOfCartException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var cart = dataLoaderService.loadCart(id);
        var group = dataLoaderService.loadGroup(cart.getGroup().getId());
        verifyIsPartOfGroup(user, group);
        verifyIsOwnerOfCart(user, cart);
        cartRepository.deleteById(id);
    }

    @Transactional
    public void addSettlementPayment(@Validated SettlementPaymentDto settlementPaymentDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(settlementPaymentDto.getGroupId());
        var member = dataLoaderService.loadUser(settlementPaymentDto.getMember().getId());
        verifyIsPartOfGroup(user, group);
        verifyIsPartOfGroup(member, group);
        createCategoryForSettlementPaymentIfNotExist(group);
        var category = dataLoaderService.loadCategoryByGroupAndName(group, SETTLEMENT_CATEGORY_NAME);
        var groupMemberCount = dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(settlementPaymentDto.getDatePurchased(), group.getId());
        createSettlementPaymentCarts(category, user, member, group, settlementPaymentDto.getAmount(), groupMemberCount, settlementPaymentDto.getDatePurchased());
    }

    public void getExcelFile(HttpServletResponse response, Long groupId) throws IOException, GroupNotFoundException, UserNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(groupId);
        verifyIsPartOfGroup(user, group);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Expires", "0");
        var cartlist = dataLoaderService.loadCartListForGroup(groupId);
        var excelWriter = new ExcelWriter(cartlist);
        excelWriter.generateExcelFile(response);
    }

    private void createSettlementPaymentCarts(Category category, User user, User member, Group group, Double amount, int groupMemberCount, Date datePurchased) {
        var cartDtoSender = new CartDto();
        cartDtoSender.setTitle("Ausgleichszahlung an " + capitalize(member.getName()));
        cartDtoSender.setDescription("");
        cartDtoSender.setDatePurchased(datePurchased);
        cartDtoSender.setAmount(amount);
        cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDtoSender, category, user, group, groupMemberCount));

        var cartDtoReceiver = new CartDto();
        cartDtoReceiver.setTitle("Ausgleichszahlung von " + capitalize(user.getName()));
        cartDtoReceiver.setDescription("");
        cartDtoReceiver.setDatePurchased(datePurchased);
        cartDtoReceiver.setAmount((-1) * amount);
        cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDtoReceiver, category, member, group, groupMemberCount));
    }

    private void verifyIsPartOfGroup(User user, Group group) throws NotOwnerOrMemberOfGroupException {
        if (!(group.getOwner().equals(user) || group.getMembers().contains(user))) {
            throw new NotOwnerOrMemberOfGroupException("User with ID " + user.getId() + " is either a member nor the owner of the group");
        }
    }

    private void verifyIsOwnerOfCart(User user, Cart cart) throws NotOwnerOfCartException {
        if (!cart.getUser().equals(user)) {
            throw new NotOwnerOfCartException("User with ID " + user.getId() + " is not the owner of the cart");
        }
    }

    private void createCategoryForSettlementPaymentIfNotExist(Group group) {
        if (categoryRepository.findCategoryByGroupAndName(group,SETTLEMENT_CATEGORY_NAME) == null) {
            categoryRepository.save(new Category(null, SETTLEMENT_CATEGORY_NAME, group, null));
        }
    }

    public void deleteCartsForUserWhereIsDeletedTrue(User user) {
        var carts = cartRepository.findCartsByUser(user);
        for (Cart cart : carts) {
            if (cart.isDeleted()) {
                cartRepository.delete(cart);
            }
        }
    }

    private String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
