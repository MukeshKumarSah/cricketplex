package com.cricketplex.service;

import com.cricketplex.entity.CommentarySubmission;
import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.CommentarySubmissionRepository;
import com.cricketplex.util.CommentaryOptionCatalog;
import com.cricketplex.util.PlaceholderValidator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CommentaryImportService {

    private final CommentarySubmissionRepository commentaryRepository;

    private static final int MAX_ROWS = 1000;

    // Column mapping: User-friendly headers
    private static final Map<String, String> COLUMN_MAP = Map.ofEntries(
            Map.entry("Commentary Text", "commentaryText"),
            Map.entry("Match Format", "matchFormat"),
            Map.entry("Phase", "phase"),
            Map.entry("Bowler Type", "bowlerType"),
            Map.entry("Event Type", "eventType"),
            Map.entry("Wicket Situation", "wicketSituation"),
            Map.entry("Extra Tags", "extraTags")
    );

    /**
     * Parse and validate Excel file
     * Returns preview with validation results
     */
    public Map<String, Object> parseAndValidate(MultipartFile file) throws IOException {
        List<Map<String, Object>> validRows = new ArrayList<>();
        List<Map<String, Object>> warningRows = new ArrayList<>();
        List<Map<String, Object>> errorRows = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                return Map.of("success", false, "error", "No header row found");
            }

            // Map column indices
            Map<String, Integer> columnIndices = mapColumns(headerRow);

            int rowCount = 0;
            for (int i = 1; i <= sheet.getLastRowNum() && rowCount < MAX_ROWS; i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;

                rowCount++;
                Map<String, Object> rowData = parseRow(row, columnIndices, i + 1);

                // Validate
                List<String> errors = validateRow(rowData);
                List<String> warnings = checkWarnings(rowData);

                if (!errors.isEmpty()) {
                    rowData.put("errors", errors);
                    errorRows.add(rowData);
                } else if (!warnings.isEmpty()) {
                    rowData.put("warnings", warnings);
                    warningRows.add(rowData);
                } else {
                    validRows.add(rowData);
                }
            }

            if (rowCount >= MAX_ROWS) {
                return Map.of(
                        "success", false,
                        "error", "Import limited to " + MAX_ROWS + " rows. Please split your file."
                );
            }
        }

        return Map.of(
                "success", true,
                "total", validRows.size() + warningRows.size() + errorRows.size(),
                "valid", validRows.size(),
                "warnings", warningRows.size(),
                "errors", errorRows.size(),
                "validRows", validRows,
                "warningRows", warningRows,
                "errorRows", errorRows
        );
    }

    /**
     * Import validated rows into database
     */
    @Transactional
    public Map<String, Object> importRows(List<Map<String, Object>> rows, User user, Team team) {
        int imported = 0;
        List<String> failures = new ArrayList<>();

        for (Map<String, Object> rowData : rows) {
            try {
                CommentarySubmission submission = createSubmission(rowData, user, team);
                commentaryRepository.save(submission);
                imported++;
            } catch (Exception e) {
                failures.add("Row " + rowData.get("rowNumber") + ": " + e.getMessage());
            }
        }

        return Map.of(
                "success", true,
                "imported", imported,
                "failed", failures.size(),
                "failures", failures
        );
    }

    /**
     * Generate Excel template for download
     */
    public Workbook generateTemplate() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Commentary Template");

        // Header row
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        int col = 0;
        for (String header : COLUMN_MAP.keySet()) {
            Cell cell = headerRow.createCell(col++);
            cell.setCellValue(header);
            cell.setCellStyle(headerStyle);
        }

        // Example row
        Row exampleRow = sheet.createRow(1);
        exampleRow.createCell(0).setCellValue("[batsman] defends solidly. [score]/[wickets] after [overs] overs.");
        exampleRow.createCell(1).setCellValue("all");
        exampleRow.createCell(2).setCellValue("all");
        exampleRow.createCell(3).setCellValue("ALL");
        exampleRow.createCell(4).setCellValue("0");
        exampleRow.createCell(5).setCellValue("");
        exampleRow.createCell(6).setCellValue("free_hit");

        // Auto-size columns
        for (int i = 0; i < COLUMN_MAP.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        return workbook;
    }

    // Private helpers

    private Map<String, Integer> mapColumns(Row headerRow) {
        Map<String, Integer> indices = new HashMap<>();
        for (Cell cell : headerRow) {
            String header = cell.getStringCellValue().trim();
            if (COLUMN_MAP.containsKey(header)) {
                indices.put(COLUMN_MAP.get(header), cell.getColumnIndex());
            }
        }
        return indices;
    }

    private Map<String, Object> parseRow(Row row, Map<String, Integer> columnIndices, int rowNumber) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rowNumber", rowNumber);

        for (Map.Entry<String, Integer> entry : columnIndices.entrySet()) {
            String field = entry.getKey();
            int colIndex = entry.getValue();
            Cell cell = row.getCell(colIndex);
            data.put(field, getCellValue(cell));
        }

        return data;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private List<String> validateRow(Map<String, Object> rowData) {
        List<String> errors = new ArrayList<>();

        // Required fields
        if (isEmpty(rowData.get("commentaryText"))) {
            errors.add("Commentary text is required");
        } else {
            String text = (String) rowData.get("commentaryText");
            if (text.length() < 10) errors.add("Commentary too short (min 10 chars)");
            if (text.length() > 500) errors.add("Commentary too long (max 500 chars)");

            // Validate placeholders
            List<String> invalid = PlaceholderValidator.validatePlaceholders(text);
            if (!invalid.isEmpty()) {
                errors.add("Invalid placeholders: " + String.join(", ", invalid));
            }
        }

        if (isEmpty(rowData.get("matchFormat"))) errors.add("Match format is required");
        if (isEmpty(rowData.get("phase"))) errors.add("Phase is required");
        if (isEmpty(rowData.get("bowlerType"))) errors.add("Bowler type is required");
        if (isEmpty(rowData.get("eventType"))) errors.add("Event type is required");

        String matchFormat = (String) rowData.get("matchFormat");
        String phase = (String) rowData.get("phase");
        String bowlerType = (String) rowData.get("bowlerType");
        String eventType = (String) rowData.get("eventType");
        String wicketSituation = (String) rowData.get("wicketSituation");

        if (!isEmpty(matchFormat) && !CommentaryOptionCatalog.MATCH_FORMATS.contains(matchFormat)) {
            errors.add("Invalid match format: " + matchFormat);
        }
        if (!isEmpty(phase) && !CommentaryOptionCatalog.PHASES.contains(phase)) {
            errors.add("Invalid phase: " + phase);
        }
        if (!isEmpty(bowlerType) && !CommentaryOptionCatalog.BOWLER_TYPES.contains(bowlerType)) {
            errors.add("Invalid bowler type: " + bowlerType);
        }
        if (!isEmpty(eventType) && !CommentaryOptionCatalog.EVENT_TYPES.contains(eventType)) {
            errors.add("Invalid event type: " + eventType);
        }

        if (!isEmpty(eventType) && CommentaryOptionCatalog.RUN_OUT_EVENTS.contains(eventType)) {
            if (isEmpty(wicketSituation)) {
                errors.add("Wicket situation is required for run-out events");
            } else if (!CommentaryOptionCatalog.WICKET_SITUATIONS.contains(wicketSituation)) {
                errors.add("Invalid wicket situation: " + wicketSituation);
            }
        } else if (!isEmpty(wicketSituation)) {
            errors.add("Wicket situation should be empty for non run-out events");
        }

        String extraTags = (String) rowData.get("extraTags");
        if (extraTags != null && !extraTags.trim().isEmpty()) {
            String[] tags = Arrays.stream(extraTags.split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .toArray(String[]::new);
            if (tags.length > 1) {
                errors.add("Only one extra tag is allowed");
            }
            for (String tag : tags) {
                if (!CommentaryOptionCatalog.EXTRA_TAGS.contains(tag)) {
                    errors.add("Invalid extra tag: " + tag);
                }
            }
        }

        return errors;
    }

    private List<String> checkWarnings(Map<String, Object> rowData) {
        List<String> warnings = new ArrayList<>();

        String text = (String) rowData.get("commentaryText");
        String eventType = (String) rowData.get("eventType");

        // Check duplicate
        if (commentaryRepository.existsByCommentaryTextAndEventType(text, eventType)) {
            warnings.add("Exact duplicate exists");
        }

        // Check similarity
        List<CommentarySubmission> similar = commentaryRepository.findSimilarCommentary(text, eventType);
        if (!similar.isEmpty()) {
            warnings.add("Similar commentary exists");
        }

        return warnings;
    }

    private boolean isEmpty(Object value) {
        return value == null || value.toString().trim().isEmpty();
    }

    private CommentarySubmission createSubmission(Map<String, Object> rowData, User user, Team team) {
        String text = (String) rowData.get("commentaryText");
        List<String> placeholders = PlaceholderValidator.extractPlaceholders(text);
        String wicketSituation = (String) rowData.get("wicketSituation");
        String normalizedWicketSituation =
            wicketSituation == null || wicketSituation.isBlank() ? null : wicketSituation.trim();

        // Parse extra tags if present
        String extraTagsStr = (String) rowData.get("extraTags");
        String extraTagsJson = null;
        if (extraTagsStr != null && !extraTagsStr.trim().isEmpty()) {
            String[] tags = Arrays.stream(extraTagsStr.split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .toArray(String[]::new);
            if (tags.length > 0) {
                extraTagsJson = "[\"" + String.join("\",\"", tags) + "\"]";
            }
        }

        return CommentarySubmission.builder()
                .user(user)
                .team(team)
                .commentaryText(text.trim())
                .status("pending")
                .matchFormat((String) rowData.get("matchFormat"))
                .phase((String) rowData.get("phase"))
                .bowlerType((String) rowData.get("bowlerType"))
                .eventType((String) rowData.get("eventType"))
                .wicketSituation(normalizedWicketSituation)
                .extraTags(extraTagsJson)
                .placeholdersUsed(PlaceholderValidator.toJsonArray(placeholders))
                .build();
    }
}
