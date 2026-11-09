package com.github.svenfran.budgetapp.budgetappbackend.helper;

import com.github.svenfran.budgetapp.budgetappbackend.helper.export.CartExportRow;
import com.github.svenfran.budgetapp.budgetappbackend.helper.export.GroupExport;
import com.github.svenfran.budgetapp.budgetappbackend.helper.export.MembershipExportRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * XLSX-Export (Apache POI). Eine Gruppe: Blätter „Ausgaben“ und „Mitgliedszeitraum“ wie bisher; alle Gruppen (DIV-68):
 * zwei Blätter je Gruppe, Namen über {@link ExportNames}. Ohne Gruppen zwei Blätter nur mit Kopfzeile (eine
 * Arbeitsmappe braucht mindestens ein Blatt).
 */
public class ExcelWriter {

    private final List<GroupExport> groups;
    private final boolean allGroups;
    private final Translator translator;

    public ExcelWriter(List<GroupExport> groups, boolean allGroups, Translator translator) {
        if (!allGroups && groups.size() > 1) {
            throw new IllegalArgumentException("Einzelgruppen-Export mit " + groups.size() + " Gruppen");
        }
        this.groups = groups;
        this.allGroups = allGroups;
        this.translator = translator;
    }

    /** Schreibt die Arbeitsmappe in den Stream; der Stream bleibt offen (gehört dem Aufrufer). */
    public void write(OutputStream outputStream) throws IOException {
        try (var workbook = new XSSFWorkbook()) {
            var dateStyle = createDateCellStyle(workbook);
            String cartSheetName = translator.translate("download.cart.sheetname");
            String membershipSheetName = translator.translate("download.membership.sheetname");

            if (groups.isEmpty()) {
                writeCartSheet(workbook.createSheet(cartSheetName), List.of(), dateStyle);
                writeMembershipSheet(workbook.createSheet(membershipSheetName), List.of(), dateStyle);
            } else if (!allGroups) {
                writeCartSheet(workbook.createSheet(cartSheetName), groups.get(0).carts(), dateStyle);
                writeMembershipSheet(workbook.createSheet(membershipSheetName), groups.get(0).memberships(), dateStyle);
            } else {
                var names = ExportNames.assign(groups, cartSheetName,
                        translator.translate("download.membership.sheetname.short"),
                        translator.translate("download.group.fallback"));
                for (GroupExport group : groups) {
                    var groupNames = names.get(group.groupId());
                    writeCartSheet(workbook.createSheet(groupNames.cartSheet()), group.carts(), dateStyle);
                    writeMembershipSheet(workbook.createSheet(groupNames.membershipSheet()), group.memberships(), dateStyle);
                }
            }
            workbook.write(outputStream);
        }
        outputStream.flush();
    }

    private void writeCartSheet(Sheet sheet, List<CartExportRow> carts, CellStyle dateStyle) {
        var header = header("download.cart.header");
        writeHeader(sheet, header);
        int rowNum = 1;
        for (CartExportRow cart : carts) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(cart.userName());
            row.createCell(1).setCellValue(cart.title());
            row.createCell(2).setCellValue(cart.description());

            var dateCell = row.createCell(3);
            dateCell.setCellValue(cart.datePurchased());
            dateCell.setCellStyle(dateStyle);

            row.createCell(4).setCellValue(cart.amount());
            row.createCell(5).setCellValue(cart.category());
            row.createCell(6).setCellValue(cart.groupName());
        }
        autoSizeColumns(sheet, header.length);
    }

    private void writeMembershipSheet(Sheet sheet, List<MembershipExportRow> memberships, CellStyle dateStyle) {
        var header = header("download.membership.header");
        writeHeader(sheet, header);
        int rowNum = 1;
        for (MembershipExportRow membership : memberships) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(membership.userName());

            Cell startDateCell = row.createCell(1);
            startDateCell.setCellValue(membership.start());
            startDateCell.setCellStyle(dateStyle);

            Cell endDateCell = row.createCell(2);
            if (membership.end() == null) {
                endDateCell.setBlank();
            } else {
                endDateCell.setCellValue(membership.end());
            }
            endDateCell.setCellStyle(dateStyle);

            row.createCell(3).setCellValue(membership.type());
        }
        autoSizeColumns(sheet, header.length);
    }

    private String[] header(String key) {
        return translator.translate(key).split("\\|");
    }

    private static void writeHeader(Sheet sheet, String[] header) {
        Row firstRow = sheet.createRow(0);
        for (int i = 0; i < header.length; i++) {
            firstRow.createCell(i).setCellValue(header[i]);
        }
    }

    private static CellStyle createDateCellStyle(Workbook workbook) {
        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd.MM.yyyy"));
        return cellStyle;
    }

    private static void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
