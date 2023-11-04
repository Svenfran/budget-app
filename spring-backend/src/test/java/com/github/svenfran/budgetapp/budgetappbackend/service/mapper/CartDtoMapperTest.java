package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class CartDtoMapperTest {

    private final CartDtoMapper cartDtoMapper = new CartDtoMapper();
    private int memberCount = 5;

    @Test
    void cartDtoToEntity_positive() throws ParseException {
        var cartEntity = cartDtoMapper.CartDtoToEntity(cartDto(), category(),cartOwner(), group(), memberCount);

        assertEquals(1L, cartEntity.getId());
        assertEquals("TestCart", cartEntity.getTitle());
        assertEquals("TestDescription", cartEntity.getDescription());
        assertEquals(55.00, cartEntity.getAmount());
        assertEquals(11, cartEntity.getAveragePerMember());
        assertEquals(dateFromString("2022-10-31"), cartEntity.getDatePurchased());
        assertEquals(1L, cartEntity.getGroup().getId());
        assertEquals(5L, cartEntity.getUser().getId());
        assertEquals("testUserCart", cartEntity.getUser().getName() );
        assertEquals(10L, cartEntity.getCategory().getId());
        assertEquals("TestCategory", cartEntity.getCategory().getName());
    }

    @Test
    void cartDtoToEntity_negative() throws ParseException {
        var cartEntity = cartDtoMapper.CartDtoToEntity(cartDto(), category(),cartOwner(), group(), memberCount);

        assertNotEquals(10L, cartEntity.getId());
        assertNotEquals("Cart", cartEntity.getTitle());
        assertNotEquals("Description", cartEntity.getDescription());
        assertNotEquals(155.00, cartEntity.getAmount());
        assertNotEquals(31.00, cartEntity.getAveragePerMember());
        assertNotEquals(dateFromString("2022-11-31"), cartEntity.getDatePurchased());
        assertNotEquals(10L, cartEntity.getGroup().getId());
        assertNotEquals(50L, cartEntity.getUser().getId());
        assertNotEquals("UserCart", cartEntity.getUser().getName() );
        assertNotEquals(100L, cartEntity.getCategory().getId());
        assertNotEquals("Category", cartEntity.getCategory().getName());
    }

    private CartDto cartDto() throws ParseException {
        var cartDto = new CartDto();
        cartDto.setId(1L);
        cartDto.setTitle("TestCart");
        cartDto.setDescription("TestDescription");
        cartDto.setAmount(55.00);
        cartDto.setDatePurchased(dateFromString("2022-10-31"));
        return cartDto;
    }

    private User cartOwner() {
        var user = new User();
        user.setId(5L);
        user.setName("testUserCart");
        return user;
    }

    private User groupOwner() {
        var user = new User();
        user.setId(8L);
        user.setName("testUserGroup");
        return user;
    }

    private Group group() {
        var group = new Group();
        group.setId(1L);
        group.setName("New Group");
        group.setOwner(groupOwner());
        return group;
    }

    private Category category() {
        var category = new Category();
        category.setId(10L);
        category.setName("TestCategory");
        return category;
    }

    private Date dateFromString(String dateString) throws ParseException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        return formatter.parse(dateString);
    }
}
