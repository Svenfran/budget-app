package com.github.svenfran.budgetapp.budgetappbackend.service.helper;

import com.github.svenfran.budgetapp.budgetappbackend.constants.TypeEnum;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.GroupMembershipHistory;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupMembershipHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class HandleGroupMembership {

    @Autowired
    private GroupMembershipHistoryRepository groupMembershipHistoryRepository;

    // Eine neue Gruppe wird erstellt
    public void startGroupMembershipForOwner(User user, Group group) {
        var groupMembership = createGroupMembership(user, group);
        groupMembership.setType(TypeEnum.OWNER);
        groupMembershipHistoryRepository.save(groupMembership);
    }

    // Ein neuer Nutzer wird hinzugefügt
    public void startGroupMembershipForMember(User user, Group group) {
        var groupMembership = createGroupMembership(user, group);
        groupMembership.setType(TypeEnum.MEMBER);
        groupMembershipHistoryRepository.save(groupMembership);
    }

    // Gruppenersteller wird zum Mitglied
    public void changeGroupOwnerToMember(User user, Group group) {
        finishGroupMembership(user, group);
        startGroupMembershipForMember(user, group);
    }

    // Gruppenmitglied wird zum Ersteller
    public void changeGroupMemberToOwner(User user, Group group) {
        finishGroupMembership(user, group);
        startGroupMembershipForOwner(user, group);
    }

    // Benutzer wird aus Gruppe entfernt
    public void finishGroupMembership(User user, Group group) {
        var groupMembership = getGroupMembershipByUserIdAndGroupIdAndMembershipEndIsNull(user, group);
        groupMembership.setMembershipEnd(new Date());
        groupMembershipHistoryRepository.save(groupMembership);
    }

    private GroupMembershipHistory getGroupMembershipByUserIdAndGroupIdAndMembershipEndIsNull(User user, Group group) {
        return groupMembershipHistoryRepository.findByUserIdAndGroupIdAndMembershipEndIsNull(user.getId(), group.getId());
    }

    private GroupMembershipHistory createGroupMembership(User user, Group group) {
        var groupMembership = new GroupMembershipHistory();
        groupMembership.setMembershipStart(new Date());
        groupMembership.setMembershipEnd(null);
        groupMembership.setUserId(user.getId());
        groupMembership.setGroupId(group.getId());
        return groupMembership;
    }
}
