package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class CartDtoMapperTest {

    private final CartDtoMapper cartDtoMapper = new CartDtoMapper();

    @Test
    void cartDtoToEntity_positive() throws ParseException {
        var cartEntity = cartDtoMapper.CartDtoToEntity(cartDto(), category(),cartOwner(), group());

        assertEquals(cartEntity.getId(), 1L);
        assertEquals(cartEntity.getTitle(), "TestCart");
        assertEquals(cartEntity.getDescription(), "TestDescription");
        assertEquals(cartEntity.getAmount(), 55.00);
        assertEquals(cartEntity.getDatePurchased(), dateFromString("2022-10-31"));
        assertEquals(cartEntity.getGroup().getId(), 1L);
        assertEquals(cartEntity.getUser().getId(), 5L);
        assertEquals(cartEntity.getUser().getUserName(), "testUserCart" );
        assertEquals(cartEntity.getCategory().getId(), 10L);
        assertEquals(cartEntity.getCategory().getName(), "TestCategory");
    }

    @Test
    void cartDtoToEntity_negative() throws ParseException {
        var cartEntity = cartDtoMapper.CartDtoToEntity(cartDto(), category(),cartOwner(), group());

        assertNotEquals(cartEntity.getId(), 10L);
        assertNotEquals(cartEntity.getTitle(), "Cart");
        assertNotEquals(cartEntity.getDescription(), "Description");
        assertNotEquals(cartEntity.getAmount(), 155.00);
        assertNotEquals(cartEntity.getDatePurchased(), dateFromString("2022-11-31"));
        assertNotEquals(cartEntity.getGroup().getId(), 10L);
        assertNotEquals(cartEntity.getUser().getId(), 50L);
        assertNotEquals(cartEntity.getUser().getUserName(), "UserCart" );
        assertNotEquals(cartEntity.getCategory().getId(), 100L);
        assertNotEquals(cartEntity.getCategory().getName(), "Category");
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
        user.setUserName("testUserCart");
        return user;
    }

    private User groupOwner() {
        var user = new User();
        user.setId(8L);
        user.setUserName("testUserGroup");
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
