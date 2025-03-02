package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.*;
import com.github.svenfran.budgetapp.budgetappbackend.entity.GroupMembershipHistory;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    GroupMembershipHistoryService gmhService;

    @Autowired
    VerificationService verificationService;

    @Autowired
    DataLoaderService dataLoaderService;

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendShoppingListNotification(Long groupId, AddEditShoppingListDto dto, String action) throws UserNotFoundException {
        var history = gmhService.getGroupMembersAndOwner(groupId);
        var user = dataLoaderService.getAuthenticatedUser();
        var destination = "/notification/" + action + "-list";

        for (var gmh : history) {
            if (!gmh.getUserId().equals(user.getId())) {
                logger.info("Notify User with id {} | {} shopping-list with id {}", gmh.getUserId(), action, dto.getId());
                messagingTemplate.convertAndSendToUser(
                        gmh.getUserId().toString(),
                        destination,
                        dto
                );
            }
        }
    }

    public void sendShoppingItemNotification(Long groupId, AddEditShoppingItemDto dto, String action) throws UserNotFoundException {
        var history = gmhService.getGroupMembersAndOwner(groupId);
        var user = dataLoaderService.getAuthenticatedUser();
        var destination = "/notification/" + action + "-item";

        for (var gmh : history) {
            if (!gmh.getUserId().equals(user.getId())) {
                logger.info("Notify User with id {} | {} shopping-item with id {}", gmh.getUserId(), action, dto.getId());
                messagingTemplate.convertAndSendToUser(
                        gmh.getUserId().toString(),
                        destination,
                        dto
                );
            }
        }
    }

    public void sendGroupUpdateNotification(Long groupId, GroupDto groupDto) throws UserNotFoundException {
        var history = gmhService.getGroupMembersAndOwner(groupId);
        var user = dataLoaderService.getAuthenticatedUser();
        var destination = "/notification/update-group";

        for (var gmh : history) {
            if (!gmh.getUserId().equals(user.getId())) {
                logger.info("Notify User with id {} | update group with id {}", gmh.getUserId(), groupDto.getId());
                messagingTemplate.convertAndSendToUser(
                        gmh.getUserId().toString(),
                        destination,
                        groupDto
                );
            }
        }
    }

    public void sendGroupDeletedNotification(List<GroupMembershipHistory> history, GroupDto groupDto) throws UserNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        var destination = "/notification/delete-group";

        for (var gmh : history) {
            if (!gmh.getUserId().equals(user.getId())) {
                logger.info("Notify User with id {} | delete group with id {}", gmh.getUserId(), groupDto.getId());
                messagingTemplate.convertAndSendToUser(
                        gmh.getUserId().toString(),
                        destination,
                        groupDto
                );
            }
        }
    }

    public void sendGroupMemberRemovedNotification(List<GroupMembershipHistory> history, GroupMembersDto groupMembersDto) throws UserNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        var destination = "/notification/remove-group-member";

        for (var gmh : history) {
            if (!gmh.getUserId().equals(user.getId())) {
                logger.info("Notify User with id {} | member removed from group with id {}", gmh.getUserId(), groupMembersDto.getId());
                messagingTemplate.convertAndSendToUser(
                        gmh.getUserId().toString(),
                        destination,
                        groupMembersDto
                );
            }
        }
    }

    public void sendGroupMemberAddedNotification(Long groupId, GroupMembersDto groupMembersDto) throws UserNotFoundException {
        var history = gmhService.getGroupMembersAndOwner(groupId);
        var user = dataLoaderService.getAuthenticatedUser();
        var destination = "/notification/add-group-member";

        for (var gmh : history) {
            if (!gmh.getUserId().equals(user.getId())) {
                logger.info("Notify User with id {} | member added to group with id {}", gmh.getUserId(), groupMembersDto.getId());
                messagingTemplate.convertAndSendToUser(
                        gmh.getUserId().toString(),
                        destination,
                        groupMembersDto
                );
            }
        }
    }

    public void sendGroupOwnerChangedNotification(Long groupId , User newOwner) throws UserNotFoundException {
        var history = gmhService.getGroupMembersAndOwner(groupId);
        var user = dataLoaderService.getAuthenticatedUser();
        var destination = "/notification/change-group-owner";

        for (var gmh : history) {
            if (!gmh.getUserId().equals(user.getId())) {
                logger.info("Notify User with id {} | new group owner with id {}", gmh.getUserId(), newOwner.getId());
                messagingTemplate.convertAndSendToUser(
                        gmh.getUserId().toString(),
                        destination,
                        new ChangeGroupOwnerDto(new UserDto(newOwner), groupId)
                );
            }
        }
    }
    
}
