package dev.slowy.core.utils;

import org.jspecify.annotations.NullMarked;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalLong;

@NullMarked
public final class AmountParser {

    private AmountParser() {}

    private static String normalizeInput(String input) {
        String s = input.trim().toLowerCase(Locale.ROOT);
        // Jika ada titik dan koma (cth 1,000.50 atau 1.000,50)
        if (s.contains(",") && s.contains(".")) {
            if (s.lastIndexOf(',') > s.lastIndexOf('.')) {
                // Format Indonesia: 1.000,50 -> 1000.50
                s = s.replace(".", "").replace(',', '.');
            } else {
                // Format US: 1,000.50 -> 1000.50
                s = s.replace(",", "");
            }
        } else if (s.contains(",")) {
            // Hanya koma: 1000,50 -> 1000.50
            s = s.replace(',', '.');
        }
        return s;
    }

    public static OptionalDouble parseMoney(String input) {
        if (input == null || input.isBlank()) return OptionalDouble.empty();

        String clean = normalizeInput(input);
        double multiplier = 1.0;

        if (clean.endsWith("k")) { multiplier = 1e3; clean = clean.substring(0, clean.length() - 1); }
        else if (clean.endsWith("m")) { multiplier = 1e6; clean = clean.substring(0, clean.length() - 1); }
        else if (clean.endsWith("b")) { multiplier = 1e9; clean = clean.substring(0, clean.length() - 1); }
        else if (clean.endsWith("t")) { multiplier = 1e12; clean = clean.substring(0, clean.length() - 1); }

        try {
            double value = Double.parseDouble(clean);
            if (!Double.isFinite(value) || value < 0) return OptionalDouble.empty();
            double total = value * multiplier;
            return (Double.isFinite(total) && total >= 0) ? OptionalDouble.of(total) : OptionalDouble.empty();
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    public static OptionalLong parseShards(String input) {
        if (input == null || input.isBlank()) return OptionalLong.empty();

        String clean = normalizeInput(input);
        long multiplier = 1L;

        if (clean.endsWith("k")) { multiplier = 1_000L; clean = clean.substring(0, clean.length() - 1); }
        else if (clean.endsWith("m")) { multiplier = 1_000_000L; clean = clean.substring(0, clean.length() - 1); }
        else if (clean.endsWith("b")) { multiplier = 1_000_000_000L; clean = clean.substring(0, clean.length() - 1); }
        else if (clean.endsWith("t")) { multiplier = 1_000_000_000_000L; clean = clean.substring(0, clean.length() - 1); }

        try {
            if (multiplier == 1L && !clean.contains(".")) {
                long val = Long.parseLong(clean);
                return val >= 0 ? OptionalLong.of(val) : OptionalLong.empty();
            }

            double value = Double.parseDouble(clean);
            if (!Double.isFinite(value) || value < 0) return OptionalLong.empty();
            double total = value * multiplier;
            if (total > Long.MAX_VALUE || total < 0) return OptionalLong.empty();

            return OptionalLong.of((long) total);
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
