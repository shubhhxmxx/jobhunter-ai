package com.jobhunter.domain;

import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Estimates years of work experience from date ranges such as "Aug 2023 - Dec 2025" or "Mar 2026 - Present". Overlaps are merged. */
public final class ExperienceEstimator {
    private static final List<String> MONTHS = List.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec");
    private static final Pattern RANGE = Pattern.compile(
            "(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?,?\\s+(\\d{4})\\s*(?:-|\u2013|\u2014|to)\\s*"
                    + "(?:(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?,?\\s+(\\d{4})|(present|current|now|till date|ongoing))");

    private ExperienceEstimator() {}

    /** Whole years, or null if no date ranges were found. */
    public static Integer estimateYears(String experienceText, YearMonth now) {
        List<int[]> spans = new ArrayList<>();
        Matcher m = RANGE.matcher(experienceText == null ? "" : experienceText.toLowerCase());
        int nowIdx = now.getYear() * 12 + now.getMonthValue() - 1;
        while (m.find()) {
            int start = Integer.parseInt(m.group(2)) * 12 + MONTHS.indexOf(m.group(1));
            int end = m.group(5) != null ? nowIdx : Integer.parseInt(m.group(4)) * 12 + MONTHS.indexOf(m.group(3));
            end = Math.min(end, nowIdx);
            if (end >= start) spans.add(new int[]{start, end + 1}); // inclusive of the end month
        }
        if (spans.isEmpty()) return null;
        spans.sort(Comparator.comparingInt(a -> a[0]));
        int total = 0, curStart = spans.get(0)[0], curEnd = spans.get(0)[1];
        for (int[] s : spans.subList(1, spans.size())) {
            if (s[0] <= curEnd) curEnd = Math.max(curEnd, s[1]);
            else { total += curEnd - curStart; curStart = s[0]; curEnd = s[1]; }
        }
        total += curEnd - curStart;
        return (int) Math.round(total / 12.0);
    }
}
