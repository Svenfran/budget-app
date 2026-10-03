package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.*;
import com.github.svenfran.budgetapp.budgetappbackend.entity.GroupMembershipHistory;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
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

    @Autowired
    HealthCheckService healthCheckService;

    private final SimpMessagingTemplate messagingTemplate;

    private final ApplicationContext applicationContext;

    public NotificationService(SimpMessagingTemplate messagingTemplate, ApplicationContext applicationContext) {
        this.messagingTemplate = messagingTemplate;
        this.applicationContext = applicationContext;
    }

    public void sendShoppingListNotification(Long groupId, AddEditShoppingListDto dto, String action) throws UserNotFoundException {
        var history = gmhService.getGroupMembersAndOwner(groupId);
        notifyMembers(history, "/notification/" + action + "-list", dto,
                "{} shopping-list with id {}", action, dto.getId());
    }

    public void sendShoppingItemNotification(Long groupId, AddEditShoppingItemDto dto, String action) throws UserNotFoundException {
        var history = gmhService.getGroupMembersAndOwner(groupId);
        notifyMembers(history, "/notification/" + action + "-item", dto,
                "{} shopping-item with id {}", action, dto.getId());
    }

    public void sendShoppingItemDeleteAllNotification(Long groupId, List<AddEditShoppingItemDto> dtos) throws UserNotFoundException {
        var history = gmhService.getGroupMembersAndOwner(groupId);
        notifyMembers(history, "/notification/delete-all-items", dtos,
                "deleted all shopping items of list with id {} and group with id {}",
                dtos.get(0).getShoppingListId(), dtos.get(0).getGroupId());
    }

    public void sendGroupUpdateNotification(Long groupId, GroupDto groupDto) throws UserNotFoundException {
        var history = gmhService.getGroupMembersAndOwner(groupId);
        notifyMembers(history, "/notification/update-group", groupDto,
                "update group with id {}", groupDto.getId());
    }

    public void sendGroupDeletedNotification(List<GroupMembershipHistory> history, GroupDto groupDto) throws UserNotFoundException {
        notifyMembers(history, "/notification/delete-group", groupDto,
                "delete group with id {}", groupDto.getId());
    }

    public void sendGroupMemberRemovedNotification(List<GroupMembershipHistory> history, GroupMembersDto groupMembersDto) throws UserNotFoundException {
        notifyMembers(history, "/notification/remove-group-member", groupMembersDto,
                "member removed from group with id {}", groupMembersDto.getId());
    }

    public void sendGroupMemberAddedNotification(Long groupId, GroupMembersDto groupMembersDto) throws UserNotFoundException {
        var history = gmhService.getGroupMembersAndOwner(groupId);
        notifyMembers(history, "/notification/add-group-member", groupMembersDto,
                "member added to group with id {}", groupMembersDto.getId());
    }

    public void sendGroupOwnerChangedNotification(Long groupId , User newOwner) throws UserNotFoundException {
        var history = gmhService.getGroupMembersAndOwner(groupId);
        notifyMembers(history, "/notification/change-group-owner",
                new ChangeGroupOwnerDto(new UserDto(newOwner), groupId),
                "new group owner with id {}", newOwner.getId());
    }

    /**
     * Sends {@code payload} to every group member except the currently authenticated user.
     * The {@code detail} message (with its {@code detailArgs}) is appended to the common
     * "Notify User with id {} | " log prefix, where the leading id is the recipient.
     */
    private void notifyMembers(List<GroupMembershipHistory> history, String destination,
                               Object payload, String detail, Object... detailArgs) throws UserNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        for (var gmh : history) {
            if (!gmh.getUserId().equals(user.getId())) {
                Object[] logArgs = new Object[detailArgs.length + 1];
                logArgs[0] = gmh.getUserId();
                System.arraycopy(detailArgs, 0, logArgs, 1, detailArgs.length);
                logger.info("Notify User with id {} | " + detail, logArgs);
                messagingTemplate.convertAndSendToUser(gmh.getUserId().toString(), destination, payload);
            }
        }
    }

    @Scheduled(fixedRate = 10000)
    public void sendHealthStatus() {
        var destination = "/notification/health";
        var healthEndpoint = applicationContext.getBean(HealthEndpoint.class);
        var health = healthEndpoint.health();

        logger.info("Health Status: {}", health.getStatus());

        messagingTemplate.convertAndSend(
                destination,
                health
        );
    }

}
