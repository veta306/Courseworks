package com.example.demo.utils;

import com.example.demo.entity.Discipline;
import com.example.demo.entity.Work;
import com.example.demo.entity.enums.FileNameTemplate;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PDFTools {

    public static String extractFirstPageText(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(inputStream.readAllBytes()))) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            pdfStripper.setStartPage(1);
            pdfStripper.setEndPage(1);
            return pdfStripper.getText(document);
        }
    }

    public static Integer getNumberOfPages(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(inputStream.readAllBytes()))) {
            return document.getNumberOfPages();
        }
    }

    public static boolean isNameMentioned(String fullName, String pageText) {
        if (fullName == null || pageText == null) return false;

        List<String> variants = getVariants(fullName);

        String normalizedText = pageText.toLowerCase();

        return variants.stream().anyMatch(variant ->
                normalizedText.contains(variant.toLowerCase())
        );
    }

    public static String extractSurnameInitials(String input) {
        String cleaned = input.trim().replaceAll("\\s+", " ");
        Pattern pattern = Pattern.compile("([А-ЯІЇЄҐ][а-яіїєґ']+\\s[А-ЯІЇЄҐ]\\.\\s?[А-ЯІЇЄҐ]?\\.)");

        Matcher matcher = pattern.matcher(cleaned);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    public static List<String> getVariants(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length < 2) return new ArrayList<>();

        String lastName = parts[0];
        String firstName = parts[1];
//        String middleName = parts.length > 2 ? parts[2] : "";

        List<String> variants = new ArrayList<>();

        variants.add(lastName + " " + firstName);
        variants.add(lastName + " " + firstName.charAt(0) + ".");
//        if (!middleName.isEmpty()) {
//            variants.add(lastName + " " + firstName.charAt(0) + ". " + middleName.charAt(0) + ".");
//        }
//        if (parts.length > 2) {
        String allInitials = lastName;
        for (int i = 1; i < parts.length; i++)
            allInitials = allInitials + " " + parts[i].charAt(0) + ".";
        variants.add(allInitials);
//        }
        variants.add(fullName);

        return variants;
    }

    static Map<String, List<String>> posVars = Map.of(
            "доц.", List.of("доцент"),
            "ст. викл.", List.of("ст. викладач", "старший викладач"),
            "к.т.н.", List.of("канд. техн. наук", "кандидат техн. наук", "кандидат технічних наук"),
            "к.ф.-м.н.", List.of("канд. фіз.-мат. наук", "кандидат фіз.-мат. наук")
    );

    public static List<String> getPositionVariants(String positionName) {
        Set<String> variantPrev = Set.of(positionName);
        for(Map.Entry<String, List<String>> entry : posVars.entrySet()) {
            Set<String> variantNext = new HashSet<>();
            variantNext.addAll(variantPrev);
            boolean anyAdd = false;
            for (String s : variantPrev) {
                if (s.contains(entry.getKey())) {
                    for (String v : entry.getValue())
                        variantNext.add(s.replace(entry.getKey(), v));
                    anyAdd = true;
                }
            }
            if (anyAdd)     variantPrev = variantNext;
        }
        return variantPrev.stream().toList();
    }

    public static String getUserNameForFile(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length < 2) return fullName;

        String lastName = parts[0];
        String firstName = parts[1];
        if (parts.length == 2){
            return lastName + firstName.charAt(0);
        }
        String middleName = parts[2];
        return lastName + firstName.charAt(0) + middleName.charAt(0);
    }

    private static final Pattern PATTERN_LITERATURE = Pattern.compile(
            "^\\s*(\\d*\\s*)?(Л[ІI][ТT][ЕE][РP][АA][ТT]У[РP][АA]|[СC]ПИ[СC][ОO][КK] [ВB]И[КK][ОO][РP]И[СC][ТT][АA][НH]И[ХX] ДЖ[ЕE]Р[ЕE]Л|[СC]ПИ[СC][ОO][КK] [ВB]И[КK][ОO][РP]И[СC][ТT][АA][НH][ОO]Ї Л[ІI][ТT][ЕE][РP][АA][ТT]У[РP]И|ДЖ[ЕE][РP][ЕE]Л[АA]|П[ЕE][РP][ЕE]Л[ІI][КK] ДЖ[ЕE][РP][ЕE]Л|[ВB]И[КK][ОO][РP]И[СC][ТT][АA][НH][ІI] ДЖ[ЕE][РP][EЕ]Л[АA]|REFERENCES|БІБЛІОГРАФІЧНИЙ СПИСОК)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PATTERN_APPENDIXES_START = Pattern.compile(
            "^\\s*(\\d*\\s*)?(Д[ОO]Д[АA][ТT][КK]И|Д[ОO]Д[АA][ТT][ОO][КK]\\s+([A-ZА-ЯІЇЄҐ]|\\d+))", // Наприклад, "ДОДАТОК А", "ДОДАТОК 1"
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );


    private static Integer findAppendixStartIndex(List<String> pagesTexts) {
        if (pagesTexts == null || pagesTexts.isEmpty()) {
            return null;
        }

        int totalPages = pagesTexts.size();

        int scanStartPage = 0;
        if (totalPages > 10) { // Має сенс пропускати, тільки якщо достатньо сторінок
            scanStartPage = Math.min(totalPages - 1, Math.max(4, totalPages / 10));
        }

        for (int i = scanStartPage; i < totalPages; i++) {
            String currentPageText = pagesTexts.get(i);
            if (currentPageText == null || currentPageText.trim().isEmpty()) {
                continue;
            }

            Matcher appendixMatcher = PATTERN_APPENDIXES_START.matcher(currentPageText);
            if (appendixMatcher.find()) {
                boolean literatureFoundBefore = false;
                int literatureSearchStart = Math.max(0, i - 10); // Пошук назад на 10 сторінок
                int literatureFoundOnPage = -1;

                for (int j = i - 1; j >= literatureSearchStart; j--) {
                    String prevPageText = pagesTexts.get(j);
                    if (prevPageText != null) {
                        Matcher literatureMatcher = PATTERN_LITERATURE.matcher(prevPageText);
                        if (literatureMatcher.find()) {
                            literatureFoundBefore = true;
                            literatureFoundOnPage = j;
                            break; // Знайшли літературу, далі не шукаємо
                        }
                    }
                }

                if (literatureFoundBefore) {
                    System.out.println("Found appendix start at page (0-based index): " + i +
                            " (literature found before on page " + literatureFoundOnPage + "). This is the primary candidate.");
                    return i; // Повертаємо 0-based індекс першої сторінки додатків
                }
            }
        }

        System.out.println("No appendix start found with preceding literature. Fallback: searching for the very first appendix marker.");
        for (int i = scanStartPage; i < totalPages; i++) {
            String currentPageText = pagesTexts.get(i);
            if (currentPageText == null || currentPageText.trim().isEmpty()) {
                continue;
            }
            Matcher appendixMatcher = PATTERN_APPENDIXES_START.matcher(currentPageText);
            if (appendixMatcher.find()) {
                System.out.println("Fallback: Found first appendix marker (without strong literature confirmation) at page (0-based index): " + i);
                return i;
            }
        }

        System.out.println("No appendix start keyword found in document, even with fallback.");
        return null;
    }

    public static byte[] trimAppendicesAndGetContent(byte[] pdfOriginalContent, String name) throws IOException {
        if (pdfOriginalContent == null || pdfOriginalContent.length == 0) {
            System.err.println("trimAppendicesAndGetContent: PDF original content is null or empty.");
            return null; // Або повернути pdfOriginalContent, якщо це більш доречно
        }

        List<String> pagesTexts = new ArrayList<>();
        int originalNumberOfPages;
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfOriginalContent))) {
            originalNumberOfPages = document.getNumberOfPages();
            if (originalNumberOfPages == 0) {
                System.out.println("trimAppendicesAndGetContent: PDF document has no pages. Returning original content.");
                return pdfOriginalContent;
            }

            PDFTextStripper pdfStripper = new PDFTextStripper();
            for (int pageNum = 1; pageNum <= originalNumberOfPages; pageNum++) { // pageNum тут 1-based
                pdfStripper.setStartPage(pageNum);
                pdfStripper.setEndPage(pageNum);
                try {
                    pagesTexts.add(pdfStripper.getText(document));
                    // Files.writeString(Path.of(name.replace("\\s", "\\x20") + "_page_" + (pageNum / 10) + "" + (pageNum % 10) + ".txt"), pagesTexts.getLast());
                } catch (IOException e) {
                    System.err.println("trimAppendicesAndGetContent: IOException while reading page " + pageNum + ": " + e.getMessage() + ". Adding empty text.");
                    pagesTexts.add(""); // Додаємо порожній рядок, щоб зберегти індексацію
                }
            }
        } catch (IOException e) {
            System.err.println("trimAppendicesAndGetContent: Critical IOException while loading PDF for text extraction: " + e.getMessage());
            throw e;
        }

        // numberOfMainContentPages - це очікуваний 0-based індекс першої сторінки додатків
        Integer appendixStartIndexAsPagesToKeep = findAppendixStartIndex(pagesTexts);

        if (appendixStartIndexAsPagesToKeep == null || appendixStartIndexAsPagesToKeep <= 0) {
            System.out.println("trimAppendicesAndGetContent: No valid appendix cut point found or cut point is at the beginning. Returning original content.");
            return pdfOriginalContent;
        }

        if (appendixStartIndexAsPagesToKeep >= originalNumberOfPages) {
            System.out.println("trimAppendicesAndGetContent: Cut point is at or after the last page. No trimming needed. Returning original content.");
            return pdfOriginalContent;
        }

        try (PDDocument originalDocument = Loader.loadPDF(new RandomAccessReadBuffer(pdfOriginalContent));
             PDDocument trimmedDocument = new PDDocument()) {

            for (int i = 0; i < appendixStartIndexAsPagesToKeep; i++) { // i тут 0-based
                trimmedDocument.addPage(originalDocument.getPage(i));
            }

            if (trimmedDocument.getNumberOfPages() == 0) {
                System.err.println("trimAppendicesAndGetContent: Trimmed document would be empty. This indicates an issue. Returning original content.");
                return pdfOriginalContent;
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            trimmedDocument.save(byteArrayOutputStream);
            System.out.println("trimAppendicesAndGetContent: Successfully trimmed document. Original pages: " + originalNumberOfPages +
                    ", Trimmed pages: " + trimmedDocument.getNumberOfPages());
            return byteArrayOutputStream.toByteArray();

        } catch (IOException e) {
            System.err.println("trimAppendicesAndGetContent: IOException during PDF creation/saving after trimming: " + e.getMessage());
            throw e;
        }
    }

    public static String getFileName(Discipline discipline, Work work) {
        List<FileNameTemplate> enumList = Arrays.stream(discipline.getFileNameTemplate().split("_"))
                .map(name -> Enum.valueOf(FileNameTemplate.class, name))
                .toList();

        StringBuilder filename = new StringBuilder();
        if (work.getExternalIdCode() != null && !work.getExternalIdCode().isBlank()) {
            filename.append(work.getExternalIdCode());
            filename.append("_");
        }
        for (FileNameTemplate myEnum : enumList) {
            switch (myEnum) {
                case TYPE -> filename.append("{0}_");
                case STUDENT ->
                        filename.append(PDFTools.getUserNameForFile(work.getStudent().getName())).append("_");
                case DISCIPLINE -> filename.append(discipline.getFileName()).append("_");
                case GROUP -> filename.append((work.getStudentGroup() != null ? work.getStudentGroup() + "_" : ""));
            }
        }
        if (filename.lastIndexOf("_") == filename.length() - 1) {
            filename.deleteCharAt(filename.length() - 1);
        }
        filename.append(".pdf");

        return filename.toString();
    }
}
