package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.*;
import com.github.svenfran.budgetapp.budgetappbackend.entity.GroupMembershipHistory;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

/**
 * Sends live updates via WebSocket to the members of a group.
 * <p>
 * Delivery happens only after the triggering transaction has committed: when a transaction synchronization is
 * active, recipients and payload are determined immediately (inside the transaction), but the messages are sent
 * in {@code afterCommit}. On rollback nothing is sent. Without an active synchronization (e.g. a caller that is
 * not {@code @Transactional} and whose repository {@code save} has already committed) the messages are sent
 * immediately. Send failures are logged per recipient and never propagated, so they cannot turn an already
 * committed REST request into an error.
 */
@Service
public class NotificationService {

    Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final SimpMessagingTemplate messagingTemplate;

    private final ApplicationContext applicationContext;

    private final GroupMembershipHistoryService gmhService;

    private final DataLoaderService dataLoaderService;

    public NotificationService(SimpMessagingTemplate messagingTemplate, ApplicationContext applicationContext,
                               GroupMembershipHistoryService gmhService, DataLoaderService dataLoaderService) {
        this.messagingTemplate = messagingTemplate;
        this.applicationContext = applicationContext;
        this.gmhService = gmhService;
        this.dataLoaderService = dataLoaderService;
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
        if (dtos.isEmpty()) return;
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
     * Sends {@code payload} to every group member except the currently authenticated user, after the current
     * transaction has committed (see class comment). The {@code detail} message (with its {@code detailArgs}) is
     * appended to the common "Notify User with id {} | " log prefix, where the leading id is the recipient.
     */
    private void notifyMembers(List<GroupMembershipHistory> history, String destination,
                               Object payload, String detail, Object... detailArgs) throws UserNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        var recipientIds = history.stream()
                .map(GroupMembershipHistory::getUserId)
                .filter(userId -> !userId.equals(user.getId()))
                .toList();
        sendAfterCommit(() -> {
            for (var recipientId : recipientIds) {
                Object[] logArgs = new Object[detailArgs.length + 1];
                logArgs[0] = recipientId;
                System.arraycopy(detailArgs, 0, logArgs, 1, detailArgs.length);
                logger.info("Notify User with id {} | " + detail, logArgs);
                try {
                    messagingTemplate.convertAndSendToUser(recipientId.toString(), destination, payload);
                } catch (RuntimeException e) {
                    logger.warn("Notify User with id {} failed | {}: {}", recipientId, destination, e.toString());
                }
            }
        });
    }

    /** Runs {@code send} after the commit of the active transaction, or immediately if there is none. */
    private void sendAfterCommit(Runnable send) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    @Scheduled(fixedRate = 10000)
    public void sendHealthStatus() {
        var destination = "/notification/health";
        var healthEndpoint = applicationContext.getBean(HealthEndpoint.class);
        var health = healthEndpoint.health();

        logger.debug("Health Status: {}", health.getStatus());

        // status only: the full Health object may contain component details
        messagingTemplate.convertAndSend(destination, Map.of("status", health.getStatus().getCode()));
    }

}
