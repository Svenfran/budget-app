package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;

import java.util.Date;


public class GroupSideNavDto {

    private Long id;
    private String name;
    private Date dateCreated;
    private UserDto userDto;
    /** Gruppenwährung (ISO 4217, DIV-77). Im Request optional: fehlt sie, gilt EUR (Anlegen) bzw. bleibt sie (Ändern). */
    private String currencyCode;


    public GroupSideNavDto(Group group) {
        this.id = group.getId();
        this.name = group.getName();
        this.dateCreated = group.getDateCreated();
        this.userDto = new UserDto(group.getOwner());
        this.currencyCode = group.getCurrencyCode().name();
    }

    public GroupSideNavDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public UserDto getUserDto() {
        return userDto;
    }

    public void setUserDto(UserDto userDto) {
        this.userDto = userDto;
    }
}
