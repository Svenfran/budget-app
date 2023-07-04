package com.github.svenfran.budgetapp.budgetappbackend.helper;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public class ExcelWriter {
    private String [] headerRow = {"Benutzername", "Titel", "Beschreibung", "Datum", "Betrag", "Kategorie", "Gruppe"};
    private List<Cart> cartlist;
    private XSSFWorkbook workbook;
    private Sheet sheet;

    public ExcelWriter(List<Cart> cartlist) {
        this.cartlist = cartlist;
        workbook = new XSSFWorkbook();
    }

    public void writeExcelFile(List<Cart> cartlist) {
        sheet = workbook.createSheet("Ausgaben");
        CellStyle cellStyle = workbook.createCellStyle();
        CreationHelper creationHelper = workbook.getCreationHelper();
        cellStyle.setDataFormat(creationHelper.createDataFormat().getFormat("dd.MM.yyyy"));

        Row firstRow = sheet.createRow(0);
        for (int i = 0; i < headerRow.length; i++) firstRow.createCell(i).setCellValue(headerRow[i]);

        int rowNum = 1;

        for (Cart cart : cartlist) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(capitalize(cart.getUser().getName()));
            row.createCell(1).setCellValue(cart.getTitle());
            row.createCell(2).setCellValue(cart.getDescription());
            var cell = row.createCell(3);
            cell.setCellValue(cart.getDatePurchased());
            cell.setCellStyle(cellStyle);
            row.createCell(4).setCellValue(cart.getAmount());
            row.createCell(5).setCellValue(cart.getCategory().getName());
            row.createCell(6).setCellValue(cart.getGroup().getName());
        }

        for (int i = 0; i < headerRow.length; i++) sheet.autoSizeColumn(i);
    }

    public void generateExcelFile(HttpServletResponse response) throws IOException {
        writeExcelFile(cartlist);
        ServletOutputStream outputStream = response.getOutputStream();
        workbook.write(outputStream);
        workbook.close();
        outputStream.close();
    }

    private String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
