package com.github.svenfran.budgetapp.budgetappbackend.helper;

import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;
import com.github.svenfran.budgetapp.budgetappbackend.entity.GroupMembershipHistory;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.service.DataLoaderService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public class ExcelWriter {

    private final List<Cart> cartlist;
    private final List<GroupMembershipHistory> membershipHistoryList;
    private final XSSFWorkbook workbook;
    private final DataLoaderService dataLoaderService;
    private final Translator translator;


    public String[] getCartHeader() {
        String header = translator.translate("download.cart.header");
        return header.split("\\|");
    }

    public String[] getMembershipHeader() {
        String header = translator.translate("download.membership.header");
        return header.split("\\|");
    }

    public ExcelWriter(List<Cart> cartlist, List<GroupMembershipHistory> membershipHistoryList,
                       DataLoaderService dataLoaderService, Translator translator) {
        this.cartlist = cartlist;
        this.membershipHistoryList = membershipHistoryList;
        this.dataLoaderService = dataLoaderService;
        workbook = new XSSFWorkbook();
        this.translator = translator;
    }

    public void writeCartSheet() {
        var cartHeaderRow = getCartHeader();
        Sheet sheet = workbook.createSheet(translator.translate("download.cart.sheetname"));
        CellStyle cellStyle = createDateCellStyle();

        Row firstRow = sheet.createRow(0);
        for (int i = 0; i < cartHeaderRow.length; i++) firstRow.createCell(i).setCellValue(cartHeaderRow[i]);

        int rowNum = 1;
        for (Cart cart : cartlist) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(handleUserName(cart.getUser().getName()));
            row.createCell(1).setCellValue(cart.getTitle());
            row.createCell(2).setCellValue(cart.getDescription());

            var cell = row.createCell(3);
            cell.setCellValue(cart.getDatePurchased());
            cell.setCellStyle(cellStyle);

            row.createCell(4).setCellValue(cart.getAmount());
            row.createCell(5).setCellValue(cart.getCategory().getName());
            row.createCell(6).setCellValue(cart.getGroup().getName());
        }
        autoSizeColumns(sheet, cartHeaderRow.length);
    }

    private void writeMembershipHistorySheet() throws UserNotFoundException {
        var membershipHeaderRow = getMembershipHeader();
        Sheet sheet = workbook.createSheet(translator.translate("download.membership.sheetname"));
        CellStyle cellStyle = createDateCellStyle();

        Row firstRow = sheet.createRow(0);
        for (int i = 0; i < membershipHeaderRow.length; i++) firstRow.createCell(i).setCellValue(membershipHeaderRow[i]);

        int rowNum = 1;
        for (GroupMembershipHistory history : membershipHistoryList) {
            LocalDate start = history.getMembershipStart().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate end = (history.getMembershipEnd() != null)
                    ? history.getMembershipEnd().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    : null;

            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(handleUserName(dataLoaderService.loadUser(history.getUserId()).getName()));

            Cell startDateCell = row.createCell(1);
            startDateCell.setCellValue(start);
            startDateCell.setCellStyle(cellStyle);

            Cell endDateCell = row.createCell(2);
            if (end == null) {
                endDateCell.setBlank();
            } else {
                endDateCell.setCellValue(end);
            }
            endDateCell.setCellStyle(cellStyle);

            row.createCell(3).setCellValue(history.getType().toString());
        }
        autoSizeColumns(sheet, membershipHeaderRow.length);
    }

    private CellStyle createDateCellStyle() {
        CellStyle cellStyle = workbook.createCellStyle();
        CreationHelper creationHelper = workbook.getCreationHelper();
        cellStyle.setDataFormat(creationHelper.createDataFormat().getFormat("dd.MM.yyyy"));
        return cellStyle;
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String handleUserName(String userName) {
        return userName.equals(UserEnum.USER_DELETED.getName())
                    ? translator.translate("user.deleted")
                    : userName;
    }

    public void generateExcelFile(HttpServletResponse response) throws IOException, UserNotFoundException {
        writeCartSheet();
        writeMembershipHistorySheet();
        ServletOutputStream outputStream = response.getOutputStream();
        workbook.write(outputStream);
        workbook.close();
        outputStream.close();
    }

}
