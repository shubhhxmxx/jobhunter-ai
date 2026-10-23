package com.jobhunter.security;

import java.util.regex.Pattern;

/** Masks anything that looks like a secret before text reaches a log line, an error message or the database. */
public final class Redactor {
    private static final Pattern SECRETS = Pattern.compile("(?i)("
            + "sk-ant-[A-Za-z0-9_-]{6,}|sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|jh_[A-Za-z0-9_-]{20,}|bearer\\s+\\S+"
            + "|(?:api[_-]?key|x-api-key|app_key|app_id|authorization|passcode|password|token)\\s*[\"']?\\s*[:=]\\s*[\"']?[^\\s\"',&}]+)");

    private Redactor() {}

    public static String redact(String s) {
        return s == null ? null : SECRETS.matcher(s).replaceAll("[redacted]");
    }
}
