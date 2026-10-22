package com.jobhunter.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds Indian-rupee salaries written as "20-30 LPA", "₹25 LPA" or "12.5 lakhs". Other currencies are ignored (unknown). */
public final class SalaryParser {
    private static final Pattern LPA = Pattern.compile(
            "(\\d{1,3}(?:\\.\\d+)?)(?:\\s*(?:-|\u2013|to)\\s*(\\d{1,3}(?:\\.\\d+)?))?\\s*(?:lpa|lakhs?|lacs?)\\b",
            Pattern.CASE_INSENSITIVE);

    private SalaryParser() {}

    /** {minLpa, maxLpa} as whole numbers, or null when no INR figure is present. */
    public static int[] parseLpa(String text) {
        Matcher m = LPA.matcher(text == null ? "" : text);
        if (!m.find()) return null;
        double lo = Double.parseDouble(m.group(1));
        double hi = m.group(2) != null ? Double.parseDouble(m.group(2)) : lo;
        if (hi < lo) { double t = lo; lo = hi; hi = t; }
        if (hi > 500) return null;
        return new int[]{(int) Math.floor(lo), (int) Math.ceil(hi)};
    }
}
