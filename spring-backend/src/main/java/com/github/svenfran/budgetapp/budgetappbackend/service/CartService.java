package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.helper.ExcelWriter;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CategoryRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.CartDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.List;

@Service
public class CartService {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartDtoMapper cartDtoMapper;


    public List<CartDto> getCartsByGroupId(Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = getCurrentUser();
        var group = groupRepository.findById(groupId).
                orElseThrow(() -> new GroupNotFoundException("Get Carts: Group not found"));
        var groupOwner = group.getOwner();
        var groupMembers = group.getMembers();

        if (groupOwner.equals(user) || groupMembers.contains(user)) {
            var cartList = cartRepository.findCartsByGroupIdAndIsDeletedFalseOrderByDatePurchasedDesc(groupId);
            return cartList.stream().map(CartDto::new).toList();
        } else throw new NotOwnerOrMemberOfGroupException("Get Carts: You are either a member nor the owner of the group");
    }

    public CartDto getCartById(Long id) throws CartNotFoundException, UserNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = getCurrentUser();
        var cart = cartRepository.findById(id).
                orElseThrow(() -> new CartNotFoundException("Get Cart By Id: Cart not found"));
        var groupOwner = cart.getGroup().getOwner();
        var groupMembers = cart.getGroup().getMembers();

        if (groupOwner.equals(user) || groupMembers.contains(user)) {
            return new CartDto(cart);
        } else throw new NotOwnerOrMemberOfGroupException("Get Cart By Id: You are either a member nor the owner of the group");
    }

    public CartDto addCart(CartDto cartDto) throws AddCartCategoryNotFoundException, UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, GroupIdNotFoundException {
        if (cartDto.getGroupId() == null) {
            throw new GroupIdNotFoundException("Add Cart: Group Id for this cart is null");
        }
        var user = getCurrentUser();
        var category = categoryRepository.findById(cartDto.getCategoryDto().getId())
                .orElseThrow(() -> new AddCartCategoryNotFoundException("Add Cart: Category not found"));
        var group = groupRepository.findById(cartDto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Add Cart: Group not found"));
        var groupOwner = group.getOwner();
        var groupMembers = group.getMembers();

        if (groupOwner.equals(user) || groupMembers.contains(user)) {
            var groupMemberCount = cartRepository
                    .getGroupMemberCountForCartDatePurchased(cartDto.getDatePurchased(), group.getId());
            return new CartDto(cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDto, category, user, group, groupMemberCount)));
        } else throw new NotOwnerOrMemberOfGroupException("Add Cart: You are either a member nor the owner of the group");
    }

    public CartDto updateCart(CartDto cartDto) throws UpdateCartCategoryNotFoundException, UserNotFoundException, GroupNotFoundException, CartNotFoundException, NotOwnerOfCartException, NotOwnerOrMemberOfGroupException, GroupIdNotFoundException {
        if (cartDto.getGroupId() == null) {
            throw new GroupIdNotFoundException("Update Cart: Group Id for this cart is null");
        }
        var user = getCurrentUser();
        var cart = cartRepository.findById(cartDto.getId()).
                orElseThrow(() -> new CartNotFoundException("Update Cart: Cart not found"));
        var category = categoryRepository.findById(cartDto.getCategoryDto().getId())
                .orElseThrow(() -> new UpdateCartCategoryNotFoundException("Update Cart: Category not found"));
        var group = groupRepository.findById(cartDto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Update Cart: Group not found"));
        var groupOwner = group.getOwner();
        var groupMembers = group.getMembers();
        var cartOwner = cart.getUser();

        if (groupOwner.equals(user) || groupMembers.contains(user)) {
            if (cartOwner.equals(user)) {
                var groupMemberCount = cartRepository
                        .getGroupMemberCountForCartDatePurchased(cartDto.getDatePurchased(), group.getId());
                return new CartDto(cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDto, category, user, group, groupMemberCount)));
            } else throw new NotOwnerOfCartException("Update Cart: You are not the owner of the cart");
        } else throw new NotOwnerOrMemberOfGroupException("Update Cart: You are either a member nor the owner of the group");
    }

    public void deleteCart(Long id) throws UserNotFoundException, CartNotFoundException, GroupNotFoundException, NotOwnerOfCartException, NotOwnerOrMemberOfGroupException {
        var user = getCurrentUser();
        var cart = cartRepository.findById(id).
                orElseThrow(() -> new CartNotFoundException("Delete Cart: Cart not found"));
        var group = groupRepository.findById(cart.getGroup().getId()).
                orElseThrow(() -> new GroupNotFoundException("Delete Cart: Group not found"));
        var groupOwner = group.getOwner();
        var groupMembers = group.getMembers();
        var cartOwner = cart.getUser();

        if (groupOwner.equals(user) || groupMembers.contains(user)) {
            if (cartOwner.equals(user)) {
                cartRepository.deleteById(id);
            } else throw new NotOwnerOfCartException("Delete Cart: You are not the owner of the cart");
        } else throw new NotOwnerOrMemberOfGroupException("Delete Cart: You are either a member nor the owner of the group");
    }

    @Transactional
    public void addSettlementPayment(SettlementPaymentDto settlementPaymentDto) throws GroupIdNotFoundException, UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        if (settlementPaymentDto.getGroupId() == null) {
            throw new GroupIdNotFoundException("Add SettlementPayment: Group Id for this cart is null");
        }
        var user = getCurrentUser();
        var member = userRepository.findById(settlementPaymentDto.getMember().getId()).
                orElseThrow(() -> new UserNotFoundException("Add SettlementPayment: Member not found"));
        var group = groupRepository.findById(settlementPaymentDto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Add SettlementPayment: Group not found"));
        var groupOwner = group.getOwner();
        var groupMembers = group.getMembers();

        if (groupOwner.equals(user) || groupMembers.contains(user)) {
            if (groupOwner.equals(member) || groupMembers.contains(member)) {
                if (categoryRepository.findCategoryByGroupAndName(group,"Ausgleichszahlung") == null) {
                    categoryRepository.save(new Category(null, "Ausgleichszahlung", group, null));
                }
                var category = categoryRepository.findCategoryByGroupAndName(group, "Ausgleichszahlung");
                var groupMemberCount = cartRepository
                        .getGroupMemberCountForCartDatePurchased(new Date(), group.getId());

                var cartDtoSender = new CartDto();
                cartDtoSender.setTitle("Ausgleichszahlung an " + capitalize(member.getUserName()));
                cartDtoSender.setDatePurchased(new Date());
                cartDtoSender.setAmount(settlementPaymentDto.getAmount());
                cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDtoSender, category, user, group, groupMemberCount));

                var cartDtoReceiver = new CartDto();
                cartDtoReceiver.setTitle("Ausgleichszahlung von " + capitalize(user.getUserName()));
                cartDtoReceiver.setDatePurchased(new Date());
                cartDtoReceiver.setAmount(-1 * settlementPaymentDto.getAmount());
                cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDtoReceiver, category, member, group, groupMemberCount));

            } else throw new NotOwnerOrMemberOfGroupException("Add SettlementPayment: Receiver of the payment is either a member nor the owner of the group");
        } else throw new NotOwnerOrMemberOfGroupException("Add SettlementPayment: Sender of the payment is either a member nor the owner of the group");
    }

    public void getExcelFile(HttpServletResponse response, Long groupId) throws IOException, GroupNotFoundException, UserNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = getCurrentUser();
        var group = groupRepository.findById(groupId).
                orElseThrow(() -> new GroupNotFoundException("Get Excel File: Group not found"));

        if (group.getOwner().equals(user) || group.getMembers().contains(user)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Expires", "0");
            var cartlist = cartRepository.findCartsByGroupIdAndIsDeletedFalseOrderByDatePurchasedDesc(groupId);
            var excelWriter = new ExcelWriter(cartlist);
            excelWriter.generateExcelFile(response);
        } else throw new NotOwnerOrMemberOfGroupException("Get Excel File: You are either the owner nor a member of the group");
    }

    private String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    // TODO: Derzeit angemeldete Nutzer -> Spring Security
    private User getCurrentUser() throws UserNotFoundException {
        // Sven als Nutzer, id = 1
        var userId = UserEnum.CURRENT_USER.getId();
        return userRepository.findById(userId).
                orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));
    }
}
