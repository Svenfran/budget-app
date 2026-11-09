package com.github.svenfran.budgetapp.budgetappbackend.helper;

import com.github.svenfran.budgetapp.budgetappbackend.helper.export.CartExportRow;
import com.github.svenfran.budgetapp.budgetappbackend.helper.export.GroupExport;
import com.github.svenfran.budgetapp.budgetappbackend.helper.export.MembershipExportRow;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.github.svenfran.budgetapp.budgetappbackend.helper.CsvFormatter.*;

/**
 * CSV-Export als ZIP (DIV-68). Eine Gruppe: {@code ausgaben.csv} und {@code mitgliederhistorie.csv} im Root; alle
 * Gruppen: ein Ordner je Gruppe (Namen über {@link ExportNames}). Dateinamen und Kopfzeilen in der Sprache des
 * Requests. Jede Datei: UTF-8 mit BOM, Spalten in derselben Reihenfolge wie im XLSX.
 */
public class CsvZipWriter {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final List<GroupExport> groups;
    private final boolean allGroups;
    private final Translator translator;

    public CsvZipWriter(List<GroupExport> groups, boolean allGroups, Translator translator) {
        if (!allGroups && groups.size() > 1) {
            throw new IllegalArgumentException("Einzelgruppen-Export mit " + groups.size() + " Gruppen");
        }
        this.groups = groups;
        this.allGroups = allGroups;
        this.translator = translator;
    }

    /**
     * Schreibt das ZIP vollständig in den Stream. Der ZipOutputStream wird geschlossen (gibt den internen Deflater
     * sofort frei, auch bei einem Schreibfehler), der übergebene Stream bleibt offen – er gehört dem Aufrufer.
     */
    public void write(OutputStream outputStream) throws IOException {
        String cartFile = translator.translate("download.cart.filename") + ".csv";
        String membershipFile = translator.translate("download.membership.filename") + ".csv";

        try (var zip = new ZipOutputStream(new NonClosingOutputStream(outputStream), StandardCharsets.UTF_8)) {
            if (groups.isEmpty()) {
                writeEntry(zip, cartFile, cartCsv(List.of()));
                writeEntry(zip, membershipFile, membershipCsv(List.of()));
            } else if (!allGroups) {
                writeEntry(zip, cartFile, cartCsv(groups.get(0).carts()));
                writeEntry(zip, membershipFile, membershipCsv(groups.get(0).memberships()));
            } else {
                var names = ExportNames.assign(groups, translator.translate("download.cart.sheetname"),
                        translator.translate("download.membership.sheetname.short"),
                        translator.translate("download.group.fallback"));
                for (GroupExport group : groups) {
                    String folder = names.get(group.groupId()).folder() + "/";
                    writeEntry(zip, folder + cartFile, cartCsv(group.carts()));
                    writeEntry(zip, folder + membershipFile, membershipCsv(group.memberships()));
                }
            }
        }
    }

    private String cartCsv(List<CartExportRow> rows) {
        var csv = new StringBuilder(header("download.cart.header"));
        for (CartExportRow row : rows) {
            csv.append(line(List.of(text(row.userName()), text(row.title()), text(row.description()),
                    date(row.datePurchased()), amount(row.amount()), text(row.category()), text(row.groupName()))));
        }
        return csv.toString();
    }

    private String membershipCsv(List<MembershipExportRow> rows) {
        var csv = new StringBuilder(header("download.membership.header"));
        for (MembershipExportRow row : rows) {
            csv.append(line(List.of(text(row.userName()), date(row.start()), date(row.end()), text(row.type()))));
        }
        return csv.toString();
    }

    private String header(String key) {
        return line(Arrays.asList(translator.translate(key).split("\\|")));
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(BOM);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** Reicht alles an den Aufrufer-Stream durch, schließt ihn aber nicht (close() = flush()). */
    private static final class NonClosingOutputStream extends FilterOutputStream {

        NonClosingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length); // FilterOutputStream schriebe sonst Byte für Byte
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
