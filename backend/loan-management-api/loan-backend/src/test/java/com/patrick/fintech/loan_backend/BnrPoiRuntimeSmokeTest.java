package com.patrick.fintech.loan_backend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.junit.jupiter.api.Test;

/**
 * Guards the exact POI runtime failure that previously broke BNR XLSX export:
 * the generated STTableType OOXML schema must be present and XSSF table style
 * metadata must be callable in the same dependency graph used by production.
 */
class BnrPoiRuntimeSmokeTest {

    @Test
    void fullOoxmlTableSchemasArePresent() throws Exception {
        assertDoesNotThrow(() -> Class.forName(
                "org.openxmlformats.schemas.spreadsheetml.x2006.main.STTableType"));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("BNR");
            sheet.createRow(0).createCell(0, CellType.STRING).setCellValue("No");
            sheet.getRow(0).createCell(1, CellType.STRING).setCellValue("Borrower");
            sheet.createRow(1).createCell(0, CellType.NUMERIC).setCellValue(1);
            sheet.getRow(1).createCell(1, CellType.STRING).setCellValue("Test");

            XSSFTable table = sheet.createTable(new AreaReference(
                    new CellReference("A1"),
                    new CellReference("B2"),
                    workbook.getSpreadsheetVersion()));
            table.setName("BnrSmokeTable");
            table.setDisplayName("BnrSmokeTable");

            Method method = table.getCTTable().getClass()
                    .getMethod("isSetTableStyleInfo");
            Object result = assertDoesNotThrow(() -> method.invoke(table.getCTTable()));
            assertNotNull(result);
        }
    }
}
