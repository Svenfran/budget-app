package com.github.svenfran.budgetapp.budgetappbackend.helper.export;

import java.util.List;

/** Formatneutrale Exportdaten einer Gruppe (DIV-68); XLSX und CSV werden aus denselben Zeilen geschrieben. */
public record GroupExport(Long groupId, String groupName, List<CartExportRow> carts,
                          List<MembershipExportRow> memberships) {
}
