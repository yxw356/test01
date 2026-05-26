package com.yuki.enterprise_private_rag_qa.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Excel / CSV 表格专用解析，保留表头与行结构，便于数值类问答。
 */
@Service
public class TabularParseService {

    private static final Logger logger = LoggerFactory.getLogger(TabularParseService.class);
    private static final int MAX_ROWS_PER_CHUNK = 20;

    public boolean isTabular(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv");
    }

    public String extractText(InputStream inputStream, String fileName) throws IOException {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return parseCsv(inputStream);
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return parseExcel(inputStream, lower.endsWith(".xlsx"));
        }
        throw new IllegalArgumentException("不支持的表格文件: " + fileName);
    }

    private String parseCsv(InputStream inputStream) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).build().parse(reader)) {
            List<String> headers = parser.getHeaderNames();
            if (headers.isEmpty()) {
                return "";
            }
            List<List<String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                List<String> row = new ArrayList<>();
                for (String header : headers) {
                    row.add(record.get(header));
                }
                rows.add(row);
                if (rows.size() >= MAX_ROWS_PER_CHUNK) {
                    output.append(formatTableBlock("CSV", "默认", headers, rows));
                    rows.clear();
                }
            }
            if (!rows.isEmpty()) {
                output.append(formatTableBlock("CSV", "默认", headers, rows));
            }
        }
        logger.info("CSV 解析完成，输出长度: {}", output.length());
        return output.toString();
    }

    private String parseExcel(InputStream inputStream, boolean xlsx) throws IOException {
        StringBuilder output = new StringBuilder();
        try (Workbook workbook = xlsx ? new XSSFWorkbook(inputStream) : new HSSFWorkbook(inputStream)) {
            DataFormatter formatter = new DataFormatter();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                if (sheet == null) {
                    continue;
                }
                Row headerRow = sheet.getRow(sheet.getFirstRowNum());
                if (headerRow == null) {
                    continue;
                }
                List<String> headers = readRow(headerRow, formatter);
                if (headers.stream().allMatch(String::isBlank)) {
                    continue;
                }
                List<List<String>> rows = new ArrayList<>();
                for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        continue;
                    }
                    List<String> values = readRow(row, formatter, headers.size());
                    if (values.stream().allMatch(String::isBlank)) {
                        continue;
                    }
                    rows.add(values);
                    if (rows.size() >= MAX_ROWS_PER_CHUNK) {
                        output.append(formatTableBlock("Excel", sheet.getSheetName(), headers, rows));
                        rows.clear();
                    }
                }
                if (!rows.isEmpty()) {
                    output.append(formatTableBlock("Excel", sheet.getSheetName(), headers, rows));
                }
            }
        }
        logger.info("Excel 解析完成，输出长度: {}", output.length());
        return output.toString();
    }

    private List<String> readRow(Row row, DataFormatter formatter) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        return values;
    }

    private List<String> readRow(Row row, DataFormatter formatter, int columnCount) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < columnCount; i++) {
            Cell cell = row.getCell(i);
            values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        return values;
    }

    private String formatTableBlock(String sourceType, String sheetName, List<String> headers, List<List<String>> rows) {
        StringBuilder block = new StringBuilder();
        block.append("[TABLE source=").append(sourceType)
                .append(" sheet=").append(sheetName)
                .append(" columns=").append(String.join("|", headers))
                .append("]\n");
        for (List<String> row : rows) {
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                String value = i < row.size() ? row.get(i) : "";
                block.append(header).append("=").append(value);
                if (i < headers.size() - 1) {
                    block.append(" | ");
                }
            }
            block.append('\n');
        }
        block.append("[/TABLE]\n\n");
        return block.toString();
    }
}
