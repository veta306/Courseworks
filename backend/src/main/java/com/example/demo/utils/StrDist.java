    package com.example.demo.utils;

import java.util.*;

/**
 * @author IlyaCk a.k.a. Ilya Porublyov
 * The preferred way to use is via one of methods
 * getBestMatchAnywhere, getBestMatchRow, getBestMatchWordRow, getBestMatchWord, getBestMatchAnywhere,
 * likelyContains, likelyContainsWords, likelyContainsRows,
 * highlyLikelyContains, highlyLikelyContainsWords, highlyLikelyContainsRows.
 * Direct using of @calcStrDist is allowed too, but may seem more complicated
 */
public class StrDist {

    public enum MatchLevel {
        NOT_MATCHED,
        LOW,
        MEDIUM,
        HIGH;

        public boolean betterOrEqual(MatchLevel that) {
            switch (this) {
                case HIGH -> {
                    return true;
                }
                case MEDIUM -> {
                    return that != HIGH;
                }
                case LOW -> {
                    return that == LOW || that == NOT_MATCHED;
                }
                case NOT_MATCHED -> {
                    return that == NOT_MATCHED;
                }
            }
            System.err.println("bad case in MatchLevel.betterOrEqual");
            return false;
        }
    }

    /**
     * WHOLE_TEXT -- no substr allowed, compare with whole text only
     * ROW -- substring allowed, but should begin/end at line breaks only; substring CAN match multiple rows
     * WORD -- substring allowed, but should begin/end at begin/end of words only; substring CAN match multiple words/rows
     * ANYWHERE -- any substring, even with begin/end inside words; substring CAN match multiple words/rows
     */
    public enum SearchBorder {
        WHOLE_TEXT,
        ROW,
        WORD,
        ANYWHERE
    }

    enum KindOfEdit {
        REPLACE_OR_COPY,
        DEL,
        INS,
        SWAP,
        SWAP_THREE,
        STOP_HERE
    }

    private static boolean isWordBegin(String s, int idx) {
        return idx <= 0 || idx < s.length() &&
                (SPACES.indexOf(s.charAt(idx - 1)) != -1 ||
                        LINE_BREAKS.indexOf(s.charAt(idx - 1)) != -1 ||
                        QUOTES_OPEN.indexOf(s.charAt(idx - 1)) != -1);
    }

    private static boolean isWordEnd(String s, int idx) {
        return isJustAfterWordEnd(s, idx + 1);
    }

    private static boolean isJustAfterWordEnd(String s, int idx) {
        return idx >= s.length() || idx >= 0 &&
                (SPACES.indexOf(s.charAt(idx)) != -1 ||
                        LINE_BREAKS.indexOf(s.charAt(idx)) != -1 ||
                        QUOTES_CLOSE.indexOf(s.charAt(idx)) != -1);
    }

    private static boolean isRowBegin(String s, int idx) {
        return idx <= 0 || idx < s.length() && LINE_BREAKS.indexOf(s.charAt(idx - 1)) != -1;
    }

    private static boolean isRowEnd(String s, int idx) {
        return isLineBreak(s, idx + 1);
    }

    private static boolean isLineBreak(String s, int idx) {
        return idx >= s.length() || idx >= 0 && LINE_BREAKS.indexOf(s.charAt(idx)) != -1;
    }

    final static String SPACES = "\u0020\u00A0\u1680\u180E\u18A4" +
            "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u200B\u202F\u205F\u3000\u3164\uFEFF";
    final static String LINE_BREAKS = "\r\n\f\u000B\u001C\u001D\u001E\u001F\u2028\u2029";
    final public static String APOSTROPHES = "'\u2018\u2019\u02BC\u02BB\u02C8\u275B\u275C\uFF07";
    final static String QUOTES_OPEN = "\"\u201C\u00AB\u2039\u275D\u301D\u301F\uFF02";
    final static String QUOTES_CLOSE = "\"\u201D\u00BB\u203A\u275E\u301E\uFF02";
    final static String HYPHENS = "-\u2010\u2011\uFE63\uFF0D";
    final static String DASHES = "\u2012\u2013\u2014\u2015\u2212\uFE58";
    final static String DOTS = ".\u2024\uFE52\uFF0E";
    final static String[] keyboardEngLow = new String[] {"qwertyuiop[]", "asdfghjkl;'", "zxcvbnm,./"};
    final static String[] keyboardEngUpper = new String[] {"QWERTYUIOP{}", "ASDFGHJKL:\"", "ZXCVBNM<>?"};
    final static String[] keyboardUkrLow = new String[] {"йцукенгшщзхї", "фівапролджє", "ячсмитьбю."};
    final static String[] keyboardUkrUpper = new String[] {"ЙЦУКЕНГШЩЗХЇ", "ФІВАПРОЛДЖЄ", "ЯЧСМИТЬБЮ,"};


    private static void addNearlyLocatedKeysDiscounts(String[] layout) {
        int costForNear = 7;
        for(int i=0; i<layout.length; i++) {
            for(int j=0; j<layout[i].length(); j++) {
                if (i>1) {
                    similarCharsClasses.add(new SimilarChars("" + layout[i].charAt(j) + layout[i-1].charAt(j), costForNear));
                    similarCharsClasses.add(new SimilarChars("" + layout[i].charAt(j) + layout[i-1].charAt(j+1), costForNear));
                }
                if (j>0) {
                    similarCharsClasses.add(new SimilarChars("" + layout[i].charAt(j) + layout[i].charAt(j-1), costForNear));
                    if (i+1 < layout.length) {
                        similarCharsClasses.add(new SimilarChars("" + layout[i].charAt(j) + layout[i+1].charAt(j-1), costForNear));
                    }
                }
                if (j+1 < layout[i].length()) {
                    similarCharsClasses.add(new SimilarChars("" + layout[i].charAt(j) + layout[i].charAt(j+1), costForNear));
                    if (i+1 < layout.length) {
                        similarCharsClasses.add(new SimilarChars("" + layout[i].charAt(j) + layout[i+1].charAt(j), costForNear));
                    }
                }
            }
        }
    }

    private static void initDistRules() {
        initCheapToInsert();

        similarCharsClasses.add(new SimilarChars(SPACES, 1));
        similarCharsClasses.add(new SimilarChars(LINE_BREAKS, 1));
        similarCharsClasses.add(new SimilarChars(SPACES + LINE_BREAKS + "_\t", 3));
        similarCharsClasses.add(new SimilarChars(APOSTROPHES, 1));
        similarCharsClasses.add(new SimilarChars(QUOTES_OPEN, 1));
        similarCharsClasses.add(new SimilarChars(QUOTES_CLOSE, 1));
        similarCharsClasses.add(new SimilarChars(APOSTROPHES + QUOTES_OPEN + QUOTES_CLOSE, 5));
        similarCharsClasses.add(new SimilarChars(HYPHENS, 1));
        similarCharsClasses.add(new SimilarChars(DASHES, 1));
        similarCharsClasses.add(new SimilarChars(HYPHENS + DASHES, 4));
        similarCharsClasses.add(new SimilarChars(HYPHENS + SPACES, 9));
        similarCharsClasses.add(new SimilarChars(DOTS, 1));
        // eng and ukr
        similarCharsClasses.add(new SimilarChars("AА", 9));
        similarCharsClasses.add(new SimilarChars("BВ", 9));
        similarCharsClasses.add(new SimilarChars("CС", 6));
        similarCharsClasses.add(new SimilarChars("EЕ", 9));
        similarCharsClasses.add(new SimilarChars("HН", 9));
        similarCharsClasses.add(new SimilarChars("IІ", 5));
        similarCharsClasses.add(new SimilarChars("KК", 9));
        similarCharsClasses.add(new SimilarChars("MМ", 9));
        similarCharsClasses.add(new SimilarChars("OО", 9));
        similarCharsClasses.add(new SimilarChars("PР", 9));
        similarCharsClasses.add(new SimilarChars("TТ", 9));
        similarCharsClasses.add(new SimilarChars("XХ", 9));
        similarCharsClasses.add(new SimilarChars("aа", 9));
        similarCharsClasses.add(new SimilarChars("cс", 6));
        similarCharsClasses.add(new SimilarChars("eе", 9));
        similarCharsClasses.add(new SimilarChars("iі", 5));
        similarCharsClasses.add(new SimilarChars("oо", 9));
        similarCharsClasses.add(new SimilarChars("pр", 9));
        similarCharsClasses.add(new SimilarChars("xх", 9));
        similarCharsClasses.add(new SimilarChars("yу", 9));
        // similar ukr
        similarCharsClasses.add(new SimilarChars("ГҐ", 3));
        similarCharsClasses.add(new SimilarChars("ІЇ", 9));
        similarCharsClasses.add(new SimilarChars("ІИ", 9));
        similarCharsClasses.add(new SimilarChars("ЙИ", 9));
        similarCharsClasses.add(new SimilarChars("ЕЄ", 9));
        similarCharsClasses.add(new SimilarChars("ЕИ", 9));
        similarCharsClasses.add(new SimilarChars("ОУ", 9));
        similarCharsClasses.add(new SimilarChars("ОА", 11));
        similarCharsClasses.add(new SimilarChars("ВУ", 9));
        similarCharsClasses.add(new SimilarChars("гґ", 3));
        similarCharsClasses.add(new SimilarChars("ії", 9));
        similarCharsClasses.add(new SimilarChars("іи", 9));
        similarCharsClasses.add(new SimilarChars("йи", 9));
        similarCharsClasses.add(new SimilarChars("еє", 9));
        similarCharsClasses.add(new SimilarChars("еи", 9));
        similarCharsClasses.add(new SimilarChars("оу", 9));
        similarCharsClasses.add(new SimilarChars("оа", 11));
        similarCharsClasses.add(new SimilarChars("ву", 9));
        // same key in diff layouts
        for(int i=0; i<3; i++) {
            for (int j = 0; j < keyboardUkrLow[i].length(); j++) {
                similarCharsClasses.add(new SimilarChars("" + keyboardUkrLow[i].charAt(j) + keyboardEngLow[i].charAt(j), 9));
                similarCharsClasses.add(new SimilarChars("" + keyboardUkrUpper[i].charAt(j) + keyboardEngUpper[i].charAt(j), 9));
            }
        }
        // nearly-located keys
        addNearlyLocatedKeysDiscounts(keyboardUkrLow);
        addNearlyLocatedKeysDiscounts(keyboardUkrUpper);
        addNearlyLocatedKeysDiscounts(keyboardEngLow);
        addNearlyLocatedKeysDiscounts(keyboardEngUpper);
    }

    public static boolean canBeSpecial(char c) {
        return charToSimClasses.containsKey(c);
    }

    /**
     * Compares two chars (not strings), considering similarity.
     *
     * @param c1 One of chars to be compared.
     * @param c2 Other of chars to be compared.
     * @return 0 for the same,
     * COMMON_DIFF for completely different,
     * COMMON_DIFF / 2 for upper case and lower case of the same character,
     * something between 0 and COMMON_DIFF for pairs treated as "similar"
     */
    public static int getCharsDist(char c1, char c2) {
        if (c1 == c2)
            return 0;
        int cMax = (int) Math.max(c1, c2);
        int cMin = (int) Math.min(c1, c2);
        int code = cMin * 0x10000 + cMax;
        Integer resFromSaved = distSaved.get(code);
        if (resFromSaved != null)
            return resFromSaved;
        if (charToSimClasses.containsKey(c1) && charToSimClasses.containsKey(c2)) {
            int resCalced = COMMON_DIFF;
            for (int i : charToSimClasses.get(c1)) {
                if (charToSimClasses.get(c2).contains(i)) {
                    resCalced = Math.min(resCalced, similarCharsClasses.get(i).dist);
                }
            }
            char c1Upper = Character.toUpperCase(c1);
            char c2Upper = Character.toUpperCase(c2);
            if (c1Upper != c1 || c2Upper != c2) {
                int diffUpCased = getCharsDist(c1Upper, c2Upper);
                if (diffUpCased < resCalced)
                    resCalced = (resCalced + diffUpCased) / 2;
            }
            distSaved.put(code, resCalced);
            return resCalced;
        } else {
            if (Character.toUpperCase(c1) == Character.toUpperCase(c2)) {
                distSaved.put(code, COMMON_DIFF / 2);
                return COMMON_DIFF / 2;
            }
            distSaved.put(code, COMMON_DIFF);
            return COMMON_DIFF;
        }
    }

    private static void initCheapToInsert() {
        cheapToInsert = new HashMap<>();
        for (char c : SPACES.toCharArray()) {
            cheapToInsert.put(c, 3);
        }
        for (char c : LINE_BREAKS.toCharArray()) {
            cheapToInsert.put(c, 3);
        }
        cheapToInsert.put('\r', 1);
        for (char c : HYPHENS.toCharArray()) {
            cheapToInsert.put(c, 9);
        }
        for (char c : DOTS.toCharArray()) {
            cheapToInsert.put(c, 9);
        }
        for (char c : QUOTES_OPEN.toCharArray()) {
            cheapToInsert.put(c, 9);
        }
        for (char c : QUOTES_CLOSE.toCharArray()) {
            cheapToInsert.put(c, 9);
        }
    }

    /**
     * @author IlyaCk a.k.a. Ilya Porublyov
     */
    public static class DistResInfo {
        /**
         * Distance between strings.
         * Based on Levenshtein metrics, but is  generalized.
         * Distance 1 by standard Levenshtein metrics, when characters are significantly different,
         * corresponds to COMMON_DIFF = 16.
         */
        public final int dist;

        /**
         * Stores characters treated as "matched".
         * Keys are indices in substring, corresponding values are corresponding indices in superstring.
         * Each used key maps to exactly one value, and each used value is mapped from exactly one key.
         * Both keys and mapped values are ordered strictly ascending.
         */
        final NavigableMap<Integer, Integer> commonSubToSuper;

        /**
         * html-format of detail explain how actually found substring differs from argument subStr
         * style "ins" means that smth not present in subStr was inserted
         * style ""
         */
        public final String diffAsHtml;

        public final MatchLevel matchLevel;

        @Override
        public String toString() {
            return "DistResInfo{" +
                    "dist=" + dist +
                    ", matchLevel=" + matchLevel +
                    (commonSubToSuper != null && commonSubToSuper.size() < 20 ? ", commonSubToSuper=" + commonSubToSuper : "") +
                    (diffAsHtml != null && diffAsHtml.length() < 50 ? ", diffAsHtml=" + diffAsHtml : "") +
                    '}';
        }

        /**
         * Used when doRestoreWay is true; indices and mappings are generated here,
         * based on generalized-Levenshtein DP table.
         *
         * @param subStr   substring used in calcStrDist
         * @param superStr superstring used in calcStrDist
         * @param dp       generalized-Levenshtein DP table
         * @param choices  choices for generalized-Levenshtein DP table
         */
        private DistResInfo(String subStr, String superStr, int[][] dp, KindOfEdit[][] choices, SearchBorder left, SearchBorder right, boolean doRestoreWay) {
            int iii = subStr.length();
            int minValue = dp[iii][superStr.length()];
            int minIdx = superStr.length();

            if (right != SearchBorder.WHOLE_TEXT) {
                for (int j = 0; j < superStr.length(); j++) {
                    if (dp[iii][j + 1] <= minValue &&
                            (right == SearchBorder.ANYWHERE ||
                                    right == SearchBorder.WORD && isWordEnd(superStr, j) ||
                                    right == SearchBorder.ROW && isRowEnd(superStr, j)))
                    {
                        minValue = dp[iii][j + 1];
                        minIdx = j + 1;
                    }
                }
            }
            if (left == SearchBorder.WORD && right == SearchBorder.ROW) {
                String SPACES_EXTENDED_END = "_\t"+SPACES+DOTS+QUOTES_CLOSE;
                int minThisRowValue = Integer.MAX_VALUE / 2;
                for (int j = 0; j+1 < superStr.length(); j++) {
                    if (isRowEnd(superStr,j)) {
                        if (minThisRowValue < minValue) {
                            for (int jjj = j;
                                 jjj > 0 && dp[iii][jjj] <= dp[iii][jjj + 1] && minThisRowValue < minValue && !(isLineBreak(superStr, jjj)) &&
                                         SPACES_EXTENDED_END.indexOf(superStr.charAt(jjj)) != -1;
                                 jjj--) {
                                if (dp[iii][jjj] < minValue) {
                                    minValue = dp[iii][jjj];
                                    minIdx = jjj;
                                }
                            }
                        }
                    }
                    if (isLineBreak(superStr, j)) {
                        minThisRowValue = Integer.MAX_VALUE / 2;
                    } else {
                        if (dp[iii][j] < minThisRowValue) {
                            minThisRowValue = dp[iii][j];
                        }
                    }
                }
            }

            dist = minValue;
            int jjj = minIdx;

            if (doRestoreWay) {
                commonSubToSuper = new TreeMap<>();
                while (iii > 0 && jjj > 0) {
                    switch (choices[iii][jjj]) {
                        case REPLACE_OR_COPY -> {
                            if (subStr.charAt(iii - 1) == superStr.charAt(jjj - 1)) {
                                commonSubToSuper.put(iii - 1, jjj - 1);
                            }
                            iii--;
                            jjj--;
                        }
                        case DEL -> { iii--; }
                        case INS -> { jjj--; }
                        case SWAP -> {
                            iii -= 2;
                            jjj -= 2;
                        }
                        case SWAP_THREE -> {
                            iii -= 3;
                            jjj -= 3;
                        }
                    }
                }
                diffAsHtml = buildDiffAsHtml(superStr, subStr, left, right);
            } else {
                commonSubToSuper = null;
                diffAsHtml = "cmp not restored because you didn't pass such option";
            }
            this.matchLevel = (this.dist < 10 ? MatchLevel.HIGH :
                    (this.dist < 30 ? MatchLevel.MEDIUM :
                            (this.dist < 100 ? MatchLevel.LOW : MatchLevel.NOT_MATCHED)));
        }

        /**
         * @param additionalPenalty additional penalty to be added to dist of oldRes
         */
        private DistResInfo(DistResInfo oldRes, int additionalPenalty) {
            if (additionalPenalty < 0)
                throw new IllegalArgumentException("additionalPenalty < 0");
            this.dist = oldRes.dist + additionalPenalty;
            this.diffAsHtml = oldRes.diffAsHtml.replace("dist =", "dist = " + formatJustDist(additionalPenalty) + " +");
            this.commonSubToSuper = oldRes.commonSubToSuper;
            this.matchLevel = (this.dist < 30 ? MatchLevel.MEDIUM :
                    (this.dist < 100 ? MatchLevel.LOW : MatchLevel.NOT_MATCHED));
        }

        /**
         * Used ONLY when trivial string match occurred
         * and main (generalized-Levenshtein) algorithm is skipped.
         *
         * @param subStr substring used in calcStrDist
         * @param start  index in superStr where trivial occurrence of subStr starts
         */
        private DistResInfo(String subStr, int start, boolean doRestoreWay, String additionalComment) {
            this.dist = 0;
            this.matchLevel = MatchLevel.HIGH;
            this.diffAsHtml = "<html>\n<span class=\"good\">\n" + subStr + "\n</span>\n(dist = 0, found trivially" +
                    ((additionalComment == null || additionalComment.isBlank()) ? "" : (" &mdash; " + additionalComment)) +
                    ")\n</html>";
            if (doRestoreWay) {
                this.commonSubToSuper = new TreeMap<>();
                for (int i = 0; i < subStr.length(); i++)
                    this.commonSubToSuper.put(i, i + start);
            } else {
                this.commonSubToSuper = null;
            }
        }

        private String formatJustDist(int dist) {
            return String.format("%.1f", Math.round((double)dist / COMMON_DIFF * 10) / 10.0);
        }

        /**
         * Should be called from constructor ONLY!
         * Depends on commonSubToSuper which SHOULD be already set
         */
        private String buildDiffAsHtml(String superStr, String subStr, SearchBorder left, SearchBorder right) {
            if (commonSubToSuper == null || commonSubToSuper.isEmpty()) {
                return "<html>\n<span class=\"skip\">" + superStr + "</span>\n<span class=\"ins\">" + subStr + "</span>\n(dist = " + formatJustDist(dist) + "(?))</html>";
            }
//                if (superStr.length() < 30)
//                    System.out.println("superStr = " + superStr + " // length = " + superStr.length());
//                else
//                    System.out.println("superStr.length = " + superStr.length());
//                System.out.println("subStr = " + subStr + " // length = " + subStr.length());
//                System.out.println("commonSubToSuper: size = " + commonSubToSuper.size() + " ,  " + commonSubToSuper);

            StringBuilder sb = new StringBuilder("<html>\n<p>\n");
            int minInSuper = commonSubToSuper.firstEntry().getValue();
            int maxInSuper = commonSubToSuper.lastEntry().getValue();
            int idx = 0;
            switch (left) {
                case ANYWHERE -> idx = minInSuper;
                case WORD -> {
                    idx = minInSuper;
                    while (idx >= 0 && !isWordBegin(superStr, idx))
                        idx--;
                }
                case ROW -> {
                    idx = minInSuper;
                    while (idx >= 0 && !isRowBegin(superStr, idx))
                        idx--;
                }
                case WHOLE_TEXT -> idx = 0;
            }

            String SPACES_EXTENDED_BEGIN = "_\t" + SPACES + DOTS + QUOTES_OPEN;
            if (right == SearchBorder.WORD || right == SearchBorder.ROW) {
                while (idx < minInSuper && SPACES_EXTENDED_BEGIN.indexOf(superStr.charAt(idx)) != -1) { idx++; }
            }

            if (idx < minInSuper) {
                sb.append("<span class=\"ins\">");
                while (idx < minInSuper) {
                    sb.append(superStr.charAt(idx));
                    idx++;
                }
                sb.append("</span>");
            }

            for (int i = 0; i < subStr.length(); ) {
                int prevJ, nextJ = commonSubToSuper.getOrDefault(i, -1);
                if (nextJ > -1 && superStr.charAt(nextJ) == subStr.charAt(i)) {
                    sb.append("<span class=\"good\">");
                    while (i < subStr.length() && 0 <= nextJ && nextJ < superStr.length() && superStr.charAt(nextJ) == subStr.charAt(i)) {
                        sb.append(subStr.charAt(i));
                        prevJ = nextJ;
                        i++;
                        nextJ = commonSubToSuper.getOrDefault(i, -1);
                        if (nextJ > prevJ + 1) {
                            insertInsertedRange(superStr, commonSubToSuper.get(i - 1), commonSubToSuper.get(i), sb);
                            break;
                        }
                    }
                    sb.append("</span>");
                } else {
                    sb.append("<span class=\"skip\">");
                    while (i < subStr.length() && !(commonSubToSuper.containsKey(i))) {
                        sb.append(subStr.charAt(i));
                        i++;
                    }
                    Map.Entry<Integer, Integer> lowerEntry = commonSubToSuper.lowerEntry(i);
                    Map.Entry<Integer, Integer> ceilingEntry = commonSubToSuper.ceilingEntry(i);
                    if (lowerEntry != null && ceilingEntry != null) {
                        int lowerValue = (int) lowerEntry.getValue();
                        int ceilingValue = (int) ceilingEntry.getValue();
                        if (ceilingValue > lowerValue + 1) {
                            insertInsertedRange(superStr, lowerValue, ceilingValue, sb);
                        }
                    }
                    sb.append("</span>");
                }
            }

            if (right != SearchBorder.ANYWHERE) {
                boolean doSkip = false;
                if (right == SearchBorder.WORD || right == SearchBorder.ROW) {
                    int jjj = idx;
                    while (!(right == SearchBorder.WORD && isWordEnd(superStr, jjj) || right == SearchBorder.ROW && isRowEnd(superStr, jjj))) { jjj++; }
                    String SPACES_EXTENDED_END = "_\t" + SPACES + DOTS + QUOTES_CLOSE;
                    while (jjj > maxInSuper && SPACES_EXTENDED_END.indexOf(superStr.charAt(jjj)) != -1) { jjj--; }
                    if (jjj == maxInSuper) { doSkip = true; }
                }
                if (!doSkip) {
                    boolean spanStarted = false;
                    for (idx = maxInSuper + 1; idx < superStr.length(); idx++) {
                        if (right == SearchBorder.WORD && isJustAfterWordEnd(superStr, idx))
                            break;
                        if (right == SearchBorder.ROW && isLineBreak(superStr, idx))
                            break;
                        if (!spanStarted)
                            sb.append("<span class=\"ins\">");
                        spanStarted = true;
                        sb.append(superStr.charAt(idx));
                    }
                    if (spanStarted)
                        sb.append("</span>");
                }
            }
            sb.append("\n</p>\n(dist = ");
            sb.append(formatJustDist(this.dist));
            sb.append(")\n</html>\n");
            return sb.toString();
        }

        private void insertInsertedRange(String superStr, int jBeforeStart, int jAfterEnd, StringBuilder sb) {
            sb.append("</span>");
            sb.append("<span class=\"ins\">");
            for (int j = jBeforeStart + 1; j < jAfterEnd; j++) {
                sb.append(superStr.charAt(j));
            }
        }

    }

    private static class SimilarChars {
        /**
         * All chars which are treated as similar.
         * The same char (and even the same pair of chars) MAY appear in different instances of SimilarChars
         */
        final String chars;
        /**
         * Distance between similar chars from the list. Normally should be less than COMMON_DIFF.
         */
        final int dist;

        SimilarChars(String chars, int dist) {
            this.chars = chars;
            this.dist = dist;
            if (this.dist >= COMMON_DIFF)
                throw new IllegalArgumentException("SimilarChars (" + dist + ") dist exceeds COMMON_DIFF (" + COMMON_DIFF + "). " +
                        "It's abnormal and contradicts sense of similar characters.");
            for (char c : chars.toCharArray()) {
                if (!(charToSimClasses.containsKey(c))) {
                    charToSimClasses.put(c, new HashSet<>());
                }
                charToSimClasses.get(c).add(similarCharsClasses.size());
            }
            similarCharsClasses.add(this);
        }
    }

    static Map<Character, Integer> cheapToInsert = null;
    static List<SimilarChars> similarCharsClasses = new ArrayList<>();
    static Map<Character, Set<Integer>> charToSimClasses = new HashMap<>();
    static Map<Integer, Integer> distSaved = new HashMap<>();
    static final int COMMON_DIFF = 16;

    /**
     * @param subStr   Substring which should be searched in superStr.
     *                 Penalty doesn't depend significantly on place of differences.
     * @param superStr Superstring where to search substring.
     * @return If trivial search founds res,  Found distance between subStr and superStr; distance-as-number is returned always,
     * indices and mapping are omitted when doRestoreWay is false.
     * @see DistResInfo
     */
    private static DistResInfo tryTrivialSearch(String subStr, String superStr, SearchBorder left, SearchBorder right, int[] trivDelCosts, int[] trivInsCosts, boolean doRestoreWay) {
        if (subStr.isBlank() && superStr.isBlank()) {
            return new DistResInfo(subStr, 0, true, "both are blank; this <b><i>needs</i></b> check if it's ok");
        }
        if (subStr.equals(superStr)) {
            return new DistResInfo(subStr, 0, true, "exactly equal");
        }
        if (subStr.equalsIgnoreCase(superStr)) {
            int diff = 0;
            for(int i=0; i < subStr.length() && diff < 25; i++) {
                diff += getCharsDist(subStr.charAt(i), superStr.charAt(i));
            }
            return new DistResInfo(new DistResInfo(subStr, 0, true, "equal <b><i>ignoring case</i></b>"), Math.min(25, diff));
        }
        if (subStr.isBlank()) {  // && !(superStr.isBlank())
            int diff = 0;
            return new DistResInfo(new DistResInfo(subStr, 0, true, "substring is blank; this <b><i>needs</i></b> check if it's ok"), 25);
        }
        if (left != SearchBorder.WHOLE_TEXT || right != SearchBorder.WHOLE_TEXT) {
            int pos = superStr.indexOf(subStr);
            while(0 <= pos && pos < superStr.length() - 1) {
                if ((pos == 0 ||
                        left == SearchBorder.ANYWHERE ||
                        left == SearchBorder.ROW && isRowBegin(superStr, pos) ||
                        left == SearchBorder.WORD && isWordBegin(superStr, pos))
                        &&
                        (pos + subStr.length() == superStr.length() ||
                                right == SearchBorder.ANYWHERE ||
                                right == SearchBorder.ROW && isRowEnd(superStr, pos + subStr.length() - 1) ||
                                right == SearchBorder.WORD && isWordEnd(superStr, pos + subStr.length() - 1))
                ) {
                    return new DistResInfo(subStr, pos, doRestoreWay, "exact substring, pos = " + (pos+1));
                }
                pos = superStr.indexOf(subStr, pos+1);
            }
        }
        return null;
    }

    /**
     * @param subStr   Substring which should be searched in superStr.
     * @param superStr Superstring where to search substring.
     * @param left  Should begin of match be at begin of text, begin of row, begin of word or anywhere
     * @param right Should end of match be at end of text, end of row, end of word or anywhere
     * @return Found distance between subStr and superStr; distance-as-number and match quality (@see {@link MatchLevel}) are returned always,
     * indices, mapping and html-form of diff are omitted when doRestoreWay is false.
     * @see DistResInfo
     */
    public static DistResInfo calcStrDist(String subStr, String superStr, SearchBorder left, SearchBorder right, boolean doRestoreWay, boolean doSubtractIfLongSameSeq) {
//            if (superStr.length() < 30)
//                System.out.println("superStr = " + superStr + " // length = " + superStr.length());
//            else
//                for (int i = 0; i < superStr.length(); i++)
//                    System.out.println("superStr[" + i + "] = " + superStr.charAt(i) + " (" + (int) (superStr.charAt(i)) + ")");
//            System.out.println("subStr = " + subStr + " // length = " + subStr.length());

        if (superStr==null || superStr.isBlank()) {
            return new DistResInfo(new DistResInfo("", -1, true, "text where to search was EMPTY!"), 100500);
        }

        if (subStr==null || subStr.isBlank()) {
            return new DistResInfo(new DistResInfo("", -1, true, "pattern to be searched was EMPTY!"), 100500);
        }

        if (cheapToInsert == null) {
            initDistRules();
        }

        subStr = subStr.trim();
        superStr = superStr.trim();

        int[] trivDelCosts = new int[subStr.length()];
        for (int i = 0; i < subStr.length(); i++) {
            Integer cost = cheapToInsert.get(subStr.charAt(i));
            trivDelCosts[i] = (cost != null ? cost : COMMON_DIFF);
        }
        int[] trivInsCosts = new int[superStr.length()];
        for (int j = 0; j < superStr.length(); j++) {
            Integer cost = cheapToInsert.get(superStr.charAt(j));
            trivInsCosts[j] = (cost != null ? cost : COMMON_DIFF);
        }

        DistResInfo trivSrchRes = tryTrivialSearch(subStr, superStr, left, right, trivDelCosts, trivInsCosts, doRestoreWay);
        if (trivSrchRes != null) {
            return trivSrchRes;
        }

        int[][] dp = new int[subStr.length() + 1][superStr.length() + 1];
        KindOfEdit[][] choices = new KindOfEdit[subStr.length() + 1][superStr.length() + 1];

        int[] costDelTwo = new int[subStr.length()];
        costDelTwo[0] = Integer.MAX_VALUE / 2;
        for(int i=1; i<subStr.length(); i++) {
            costDelTwo[i] = (2*getCharsDist(subStr.charAt(i-1), subStr.charAt(i)) + trivDelCosts[i-1]) / 3;
        }
        int[] costInsTwo = new int[superStr.length()];
        costInsTwo[0] = Integer.MAX_VALUE / 2;
        for(int j=1; j<superStr.length(); j++) {
            costInsTwo[j] = (2*getCharsDist(superStr.charAt(j-1), superStr.charAt(j)) + trivInsCosts[j]) / 3;
        }

        dp[0][0] = 0;

        boolean allSpacesSinceRowBegin = true;
        boolean allSpacesSinceWordBegin = true;
        final String CAN_SKIP_AT_ROW_BEGIN = "_\t"+SPACES+QUOTES_OPEN+DOTS;
        for (int j = 1; j <= superStr.length(); j++) {
            if (isLineBreak(superStr, j-1))
                allSpacesSinceRowBegin = true;
            else if (j > 1 && CAN_SKIP_AT_ROW_BEGIN.indexOf(superStr.charAt(j-1)) == -1) {
                allSpacesSinceRowBegin = false;
            }
            if (isWordBegin(superStr, j))
                allSpacesSinceWordBegin = true;
            else if (j>1 && CAN_SKIP_AT_ROW_BEGIN.indexOf(superStr.charAt(j-1)) == -1) {
                allSpacesSinceWordBegin = false;
            }

            if (left != SearchBorder.WHOLE_TEXT) {
                if (left == SearchBorder.ANYWHERE ||
                        left == SearchBorder.WORD && (isWordBegin(superStr, j)  || allSpacesSinceWordBegin) ||
                        left == SearchBorder.ROW && (isRowBegin(superStr, j) || allSpacesSinceRowBegin))
                {
                    choices[0][j] = KindOfEdit.STOP_HERE;
                    dp[0][j] = 0;
                    continue;
                }
            }
            dp[0][j] = dp[0][j-1] + trivInsCosts[j-1];
            choices[0][j] = KindOfEdit.INS;
        }

        for(int i=1; i <= subStr.length(); i++) {
            dp[i][0] = dp[i-1][0] + trivDelCosts[i-1];
            choices[i][0] = KindOfEdit.DEL;
        }

        for (int i = 1; i <= subStr.length(); i++) {
            for (int j = 1; j <= superStr.length(); j++) {
                int costIns = trivInsCosts[j-1];
                if (choices[i][j-1] != KindOfEdit.DEL && costInsTwo[j-1] < trivInsCosts[j-1]) {
                    costIns = costInsTwo[j-1];
                }
                int minDist = dp[i][j - 1] + costIns;
                KindOfEdit minEdit = KindOfEdit.INS;

                int costDel = trivDelCosts[i-1];
                if (choices[i-1][j] != KindOfEdit.INS && costDelTwo[i-1] < trivDelCosts[i-1]) {
                    costDel = costDelTwo[i-1];
                }
                int distDel = dp[i - 1][j] + costDel;
                if (distDel < minDist) {
                    minDist = distDel;
                    minEdit = KindOfEdit.DEL;
                }

                int replCost = getCharsDist(subStr.charAt(i - 1), superStr.charAt(j - 1));
                if (doSubtractIfLongSameSeq && replCost <= 3 && i>1 && j>1) {
                    int sumReplCost = replCost;
                    int numExtraSimilar = 2;
                    int costBefore;
                    while (numExtraSimilar < i && numExtraSimilar < j &&
                            choices[i-numExtraSimilar+1][j-numExtraSimilar+1] == KindOfEdit.REPLACE_OR_COPY &&
                            (costBefore = getCharsDist(subStr.charAt(i - numExtraSimilar - 1), superStr.charAt(j - numExtraSimilar - 1))) <= 3 &&
                            (sumReplCost += costBefore) <= COMMON_DIFF / 2)
                    {
                        numExtraSimilar++;
                    }
                    if (numExtraSimilar > 2) {
                        replCost -= 1;
                        if (numExtraSimilar > 8) {
                            replCost -= (int)Math.sqrt(Math.sqrt(numExtraSimilar / 8));
                        }
                    }
                }
                int distReplace = dp[i-1][j-1] + replCost;
                if (distReplace <= minDist) {
                    minDist = distReplace;
                    minEdit = KindOfEdit.REPLACE_OR_COPY;
                }
                if (i > 1 && j > 1 && dp[i-2][j-2] < minDist) {
                    int commonOrderCost = replCost + getCharsDist(subStr.charAt(i - 2), superStr.charAt(j - 2));
                    int swappedOrderCost = getCharsDist(subStr.charAt(i - 1), superStr.charAt(j - 2)) + getCharsDist(subStr.charAt(i - 2), superStr.charAt(j - 1));
                    if (swappedOrderCost < commonOrderCost) {
                        int distForSwapped = dp[i - 2][j - 2] + (swappedOrderCost + commonOrderCost) / 2;
                        if (distForSwapped < minDist) {
                            minDist = distForSwapped;
                            minEdit = KindOfEdit.SWAP;
                        }
                        if (i > 2 && j > 2 && dp[i-3][j-3] < minDist) {
                            commonOrderCost += getCharsDist(subStr.charAt(i - 3), superStr.charAt(j - 3));
                            int swappedOrderCostTwo = getCharsDist(subStr.charAt(i - 1), superStr.charAt(j - 3)) +
                                    getCharsDist(subStr.charAt(i - 2), superStr.charAt(j - 1)) +
                                    getCharsDist(subStr.charAt(i - 3), superStr.charAt(j - 2));
                            int swappedOrderCostThree = getCharsDist(subStr.charAt(i - 3), superStr.charAt(j - 1)) +
                                    getCharsDist(subStr.charAt(i - 1), superStr.charAt(j - 2)) +
                                    getCharsDist(subStr.charAt(i - 2), superStr.charAt(j - 3));
                            swappedOrderCost = Math.min(swappedOrderCostTwo, swappedOrderCostThree);
                            if (swappedOrderCost < commonOrderCost) {
                                distForSwapped = dp[i - 3][j - 3] + (swappedOrderCost + 2 * commonOrderCost) / 3;
                                if (distForSwapped < minDist) {
                                    minDist = distForSwapped;
                                    minEdit = KindOfEdit.SWAP_THREE;
                                }
                            }
                        }
                    }
                }
                dp[i][j] = minDist;
                choices[i][j] = minEdit;
            }
        }
        return new DistResInfo(subStr, superStr, dp, choices, left, right, doRestoreWay);
    }

    public static boolean likelyContains(String subStr, String superStr) {
        return calcStrDist(subStr, superStr, SearchBorder.ANYWHERE, SearchBorder.ANYWHERE, false, true).matchLevel.betterOrEqual(MatchLevel.MEDIUM);
    }

    public static boolean likelyContainsRows(String subStr, String superStr) {
        return calcStrDist(subStr, superStr, SearchBorder.ROW, SearchBorder.ROW, false,true).matchLevel.betterOrEqual(MatchLevel.MEDIUM);
    }

    public static boolean likelyContainsWords(String subStr, String superStr) {
        return calcStrDist(subStr, superStr, SearchBorder.WORD, SearchBorder.WORD, false, true).matchLevel.betterOrEqual(MatchLevel.MEDIUM);
    }

    public static boolean likelyMatches(String subStr, String superStr) {
        return calcStrDist(subStr, superStr, SearchBorder.WHOLE_TEXT, SearchBorder.WHOLE_TEXT, false, true).matchLevel.betterOrEqual(MatchLevel.MEDIUM);
    }

    public static boolean highlyLikelyContains(String subStr, String superStr) {
        return calcStrDist(subStr, superStr, SearchBorder.ANYWHERE, SearchBorder.ANYWHERE, false, false).matchLevel == MatchLevel.HIGH;
    }

    public static boolean highlyLikelyContainsRows(String subStr, String superStr) {
        return calcStrDist(subStr, superStr, SearchBorder.ROW, SearchBorder.ROW, false, false).matchLevel == MatchLevel.HIGH;
    }

    public static boolean highlyLikelyContainsWords(String subStr, String superStr) {
        return calcStrDist(subStr, superStr, SearchBorder.WORD, SearchBorder.WORD, false, false).matchLevel == MatchLevel.HIGH;
    }

    public static boolean highlyLikelyMatches(String subStr, String superStr) {
        return calcStrDist(subStr, superStr, SearchBorder.WHOLE_TEXT, SearchBorder.WHOLE_TEXT, false, false).matchLevel == MatchLevel.HIGH;
    }

    public static DistResInfo getBestMatch___(String substr, String str, SearchBorder left, SearchBorder right, boolean doRestoreWay) {
        if (substr == null || substr.isBlank() || str == null || str.isBlank()) {
            return new DistResInfo(new DistResInfo("", -1, true, "pattern to be searched was EMPTY!"), 100500);
        }
        DistResInfo distInfo = calcStrDist(substr, str, left, right, doRestoreWay, false);
        if (distInfo.matchLevel.betterOrEqual(MatchLevel.MEDIUM)) {
            return distInfo;
        }
        String substrUpper = substr.toUpperCase(Locale.ROOT);
        String strUpper = str.toUpperCase(Locale.ROOT);
        if (!substr.equals(substrUpper) || !str.equals(strUpper)) {
            DistResInfo distInfoUpperCase = new DistResInfo(
                    calcStrDist(substrUpper, str.toUpperCase(Locale.ROOT), left, right, doRestoreWay, false),
                    25);
            if (distInfoUpperCase.dist < distInfo.dist) {
                distInfo = distInfoUpperCase;
                if (distInfo.matchLevel.betterOrEqual(MatchLevel.MEDIUM)) {
                    return distInfo;
                }
            }
        }
//        DistResInfo distInfoSubtractIfCommonSeq = new DistResInfo(
//                calcStrDist(substr, str, left, right, doRestoreWay, true),
//                40);
//        if (distInfoSubtractIfCommonSeq.dist < distInfo.dist) {
//            distInfo = distInfoSubtractIfCommonSeq;
//            if (distInfo.matchLevel.betterOrEqual(MatchLevel.MEDIUM)) {
//                return distInfo;
//            }
//        }
//        if (!substr.equals(substrUpper)) {
//            DistResInfo distInfoUpperCaseSubtractIfCommonSeq = new DistResInfo(
//                    calcStrDist(substrUpper, str.toUpperCase(Locale.ROOT), left, right, doRestoreWay, false),
//                    75);
//            if (distInfoUpperCaseSubtractIfCommonSeq.dist < distInfo.dist) {
//                distInfo = distInfoUpperCaseSubtractIfCommonSeq;
//            }
//        }
        return distInfo;
    }

    public static DistResInfo getBestMatchAnywhere(String substr, String str, boolean doRestoreWay) {
        return getBestMatch___(substr, str, SearchBorder.ANYWHERE, SearchBorder.ANYWHERE, doRestoreWay);
    }

    public static DistResInfo getBestMatchWord(String substr, String str, boolean doRestoreWay) {
        return getBestMatch___(substr, str, SearchBorder.WORD, SearchBorder.WORD, doRestoreWay);
    }

    public static DistResInfo getBestMatchWordRow(String substr, String str, boolean doRestoreWay) {
        return getBestMatch___(substr, str, SearchBorder.WORD, SearchBorder.ROW, doRestoreWay);
    }

    public static DistResInfo getBestMatchRow(String substr, String str, boolean doRestoreWay) {
        return getBestMatch___(substr, str, SearchBorder.ROW, SearchBorder.ROW, doRestoreWay);
    }

    public static DistResInfo getBestMatchWhole(String substr, String str, boolean doRestoreWay) {
        return getBestMatch___(substr, str, SearchBorder.WHOLE_TEXT, SearchBorder.WHOLE_TEXT, doRestoreWay);
    }

}

