package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.constants.ExportFormat;
import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.GroupMembershipHistory;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.GroupNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOrMemberOfGroupException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.helper.CsvZipWriter;
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils;
import com.github.svenfran.budgetapp.budgetappbackend.helper.ExcelWriter;
import com.github.svenfran.budgetapp.budgetappbackend.helper.Translator;
import com.github.svenfran.budgetapp.budgetappbackend.helper.export.CartExportRow;
import com.github.svenfran.budgetapp.budgetappbackend.helper.export.GroupExport;
import com.github.svenfran.budgetapp.budgetappbackend.helper.export.MembershipExportRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ausgabenexport (DIV-68): lädt die Gruppen des Exports und bereitet ihre Ausgaben und Mitgliederhistorie
 * formatneutral auf. Rein lesend; welche Gruppen exportiert werden, entscheidet ausschließlich der Server.
 */
@Service
public class ExportService {

    private final DataLoaderService dataLoaderService;
    private final VerificationService verificationService;
    private final GroupService groupService;
    private final Translator translator;

    public ExportService(DataLoaderService dataLoaderService, VerificationService verificationService,
                         GroupService groupService, Translator translator) {
        this.dataLoaderService = dataLoaderService;
        this.verificationService = verificationService;
        this.groupService = groupService;
        this.translator = translator;
    }

    static final String CONTENT_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String CONTENT_TYPE_ZIP = "application/zip";

    /**
     * Schreibt den Export in die Response. Laden und Berechtigungsprüfung laufen vollständig, bevor Header gesetzt
     * oder Bytes geschrieben werden; Fehler werden dadurch normal gemappt (403/404). Eigene Transaktion, weil der
     * Aufruf von loadExportData ein Selbstaufruf ist (am Proxy vorbei).
     *
     * @param groupId mit Angabe nur diese Gruppe, ohne Angabe alle Gruppen des Nutzers (siehe loadExportData)
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void export(ExportFormat format, Long groupId, HttpServletResponse response)
            throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, IOException {
        var groups = loadExportData(groupId);
        boolean allGroups = groupId == null;
        response.setContentType(format == ExportFormat.XLSX ? CONTENT_TYPE_XLSX : CONTENT_TYPE_ZIP);
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Expires", "0");
        if (format == ExportFormat.XLSX) {
            new ExcelWriter(groups, allGroups, translator).write(response.getOutputStream());
        } else {
            new CsvZipWriter(groups, allGroups, translator).write(response.getOutputStream());
        }
    }

    /**
     * Ein Snapshot über alle exportierten Gruppen. Öffentlich und transaktional, damit auch Aufrufe von außen (über den
     * Spring-Proxy) Lazy Loading in einer eigenen Transaktion ausführen.
     *
     * @param groupId mit Angabe nur diese Gruppe (Nutzer muss Owner oder Mitglied sein), ohne Angabe alle Gruppen, in
     *                denen der Nutzer aktuell Owner oder Mitglied ist, jede genau einmal und nach ID sortiert
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<GroupExport> loadExportData(Long groupId)
            throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        List<Group> groups;
        if (groupId != null) {
            var group = dataLoaderService.loadGroup(groupId);
            verificationService.verifyIsPartOfGroup(user, group);
            groups = List.of(group);
        } else {
            groups = groupService.getDistinctGroupsByMemberOrOwner();
        }

        Map<Long, String> userNames = new HashMap<>();
        List<GroupExport> result = new ArrayList<>();
        for (Group group : groups) {
            result.add(toGroupExport(group, userNames));
        }
        return result;
    }

    private GroupExport toGroupExport(Group group, Map<Long, String> userNames) throws UserNotFoundException {
        var carts = dataLoaderService.loadCartListForGroup(group.getId()).stream()
                .map(cart -> new CartExportRow(
                        displayName(cart.getUser().getName()),
                        cart.getTitle(),
                        cart.getDescription(),
                        DateUtils.toLocalDate(cart.getDatePurchased()),
                        cart.getAmount(),
                        cart.getCategory().getName(),
                        cart.getGroup().getName()))
                .toList();

        List<MembershipExportRow> memberships = new ArrayList<>();
        for (GroupMembershipHistory history : dataLoaderService.loadMembershipHistoryForGroup(group.getId())) {
            memberships.add(new MembershipExportRow(
                    userName(history.getUserId(), userNames),
                    DateUtils.toLocalDate(history.getMembershipStart()),
                    history.getMembershipEnd() != null ? DateUtils.toLocalDate(history.getMembershipEnd()) : null,
                    history.getType().toString()));
        }
        return new GroupExport(group.getId(), group.getName(), carts, memberships);
    }

    /** Ein Nutzer wird pro Export nur einmal geladen, auch wenn er in mehreren Gruppen bzw. Mitgliedschaften vorkommt. */
    private String userName(Long userId, Map<Long, String> userNames) throws UserNotFoundException {
        var name = userNames.get(userId);
        if (name == null) {
            name = displayName(dataLoaderService.loadUser(userId).getName());
            userNames.put(userId, name);
        }
        return name;
    }

    private String displayName(String userName) {
        return UserEnum.USER_DELETED.getName().equals(userName) ? translator.translate("user.deleted") : userName;
    }
}
