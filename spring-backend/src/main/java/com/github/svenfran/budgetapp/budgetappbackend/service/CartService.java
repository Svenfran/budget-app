package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
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

    @Autowired
    private VerificationService verificationService;

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

    public CartDto addCart(@Validated CartDto cartDto) throws Exception {
        var user = dataLoaderService.getAuthenticatedUser();
        var category = dataLoaderService.loadCategory(cartDto.getCategoryDto().getId());
        var group = dataLoaderService.loadGroup(cartDto.getGroupId());
        var gmh = dataLoaderService.loadMembershipHistoryForGroupAndUser(group.getId(), user.getId());
        verificationService.verifyIsPartOfGroup(user, group);
        verificationService.verifyDatePurchasedWithinMembershipPeriod(gmh, cartDto.getDatePurchased());
        var groupMemberCount = dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(cartDto.getDatePurchased(), group.getId());
        return new CartDto(cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDto, category, user, group, groupMemberCount)));
    }

    public CartDto updateCart(@Validated CartDto cartDto) throws Exception {
        var user = dataLoaderService.getAuthenticatedUser();
        var cart = dataLoaderService.loadCart(cartDto.getId());
        var group = dataLoaderService.loadGroup(cartDto.getGroupId());
        var gmh = dataLoaderService.loadMembershipHistoryForGroupAndUser(group.getId(), user.getId());
        verificationService.verifyIsPartOfGroup(user, group);
        verificationService.verifyIsOwnerOfCart(user, cart);
        verificationService.verifyDatePurchasedWithinMembershipPeriod(gmh, cartDto.getDatePurchased());
        var category = dataLoaderService.loadCategory(cartDto.getCategoryDto().getId());
        var groupMemberCount = dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(cartDto.getDatePurchased(), group.getId());
        return new CartDto(cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDto, category, user, group, groupMemberCount)));
    }

    public void deleteCart(Long id) throws UserNotFoundException, CartNotFoundException, GroupNotFoundException, NotOwnerOfCartException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var cart = dataLoaderService.loadCart(id);
        var group = dataLoaderService.loadGroup(cart.getGroup().getId());
        verificationService.verifyIsPartOfGroup(user, group);
        verificationService.verifyIsOwnerOfCart(user, cart);
        cartRepository.deleteById(id);
    }

    @Transactional
    public void addSettlementPayment(@Validated SettlementPaymentDto settlementPaymentDto) throws Exception {
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
        createSettlementPaymentCarts(category, user, member, group, settlementPaymentDto.getAmount(), groupMemberCount, settlementPaymentDto.getDatePurchased());
    }

    public void getExcelFile(HttpServletResponse response, Long groupId) throws IOException, GroupNotFoundException, UserNotFoundException, NotOwnerOrMemberOfGroupException, IllegalAccessException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(groupId);
        verificationService.verifyIsPartOfGroup(user, group);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Expires", "0");
        var cartlist = dataLoaderService.loadCartListForGroup(groupId);
        var excelWriter = new ExcelWriter(cartlist);
        excelWriter.generateExcelFile(response);
    }

    private void createSettlementPaymentCarts(Category category, User user, User member, Group group, Double amount, int groupMemberCount, Date datePurchased) {
        var cartDtoSender = new CartDto();
        cartDtoSender.setTitle("Ausgleichszahlung an " + member.getName());
        cartDtoSender.setDescription("");
        cartDtoSender.setDatePurchased(datePurchased);
        cartDtoSender.setAmount(amount);
        cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDtoSender, category, user, group, groupMemberCount));

        var cartDtoReceiver = new CartDto();
        cartDtoReceiver.setTitle("Ausgleichszahlung von " + user.getName());
        cartDtoReceiver.setDescription("");
        cartDtoReceiver.setDatePurchased(datePurchased);
        cartDtoReceiver.setAmount((-1) * amount);
        cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDtoReceiver, category, member, group, groupMemberCount));
    }


    private void createCategoryForSettlementPaymentIfNotExist(Group group) {
        if (categoryRepository.findCategoryByGroupAndName(group, SETTLEMENT_CATEGORY_NAME) == null) {
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

}
