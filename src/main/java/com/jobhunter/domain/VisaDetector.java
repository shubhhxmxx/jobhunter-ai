package com.jobhunter.domain;

import java.util.regex.Pattern;

/** Reads visa and relocation statements in a posting. Only explicit wording counts; silence is UNKNOWN, never YES. */
public final class VisaDetector {
    public enum Visa { YES, NO, UNKNOWN }

    private static final Pattern NO = Pattern.compile("(?i)no\\s+(?:visa\\s+)?sponsorship"
            + "|(?:cannot|can't|can not|unable to|won't|will not|not able to|do not|don't|does not|doesn't)\\s+(?:currently\\s+)?(?:offer\\s+|provide\\s+)?(?:visa\\s+)?sponsor"
            + "|not\\s+(?:offer|provide)\\s+(?:visa\\s+)?sponsorship"
            + "|without\\s+(?:the\\s+need\\s+for\\s+)?(?:visa\\s+)?sponsorship"
            + "|must\\s+be\\s+(?:legally\\s+)?(?:authorized|authorised|eligible)\\s+to\\s+work"
            + "|visa\\s+sponsorship\\s+(?:is\\s+)?not");
    private static final Pattern YES = Pattern.compile("(?i)visa\\s+(?:sponsorship|support|assistance|relocation)"
            + "|sponsor(?:s|ing)?\\s+(?:a\\s+|your\\s+|work\\s+)?visas?"
            + "|we\\s+(?:can\\s+|will\\s+)?sponsor"
            + "|relocation\\s+(?:assistance|package|support|bonus|help|budget)"
            + "|work\\s+permit\\s+(?:support|assistance|sponsorship)"
            + "|blue\\s+card|visa\\s+provided|(?:offer|provide)s?\\s+(?:visa|relocation)");

    private VisaDetector() {}

    public static Visa detect(String text) {
        String t = text == null ? "" : text;
        if (NO.matcher(t).find()) return Visa.NO;
        return YES.matcher(t).find() ? Visa.YES : Visa.UNKNOWN;
    }
}
