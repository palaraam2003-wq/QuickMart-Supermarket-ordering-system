package com.rapidmart.delivery.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SlotUtils {

    private SlotUtils() {
    }

    /**
     * Parses the end time of a delivery time slot string.
     * Supports formats:
     * - "MORNING_09_12" -> 12:00
     * - "AFTERNOON_12_15" -> 15:00
     * - "EVENING_15_18" -> 18:00
     * - "NIGHT_18_21" -> 21:00
     * - "10:00-12:00" -> 12:00
     * - "09:00-11:00" -> 11:00
     */
    public static LocalTime getSlotEndTime(String slot) {
        if (slot == null || slot.isBlank()) {
            return LocalTime.of(23, 59, 59);
        }
        String s = slot.trim();

        // Check format xx:xx-yy:yy
        if (s.contains("-")) {
            String[] parts = s.split("-");
            if (parts.length == 2) {
                try {
                    String endTimeStr = parts[1].trim();
                    if (endTimeStr.length() == 5) {
                        return LocalTime.parse(endTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // Check format MORNING_09_12 or similar
        if (s.contains("_")) {
            String[] parts = s.split("_");
            String last = parts[parts.length - 1];
            try {
                int hour = Integer.parseInt(last);
                if (hour >= 0 && hour <= 23) {
                    return LocalTime.of(hour, 0);
                }
            } catch (Exception ignored) {
            }
        }

        return LocalTime.of(23, 59, 59);
    }

    public static LocalDateTime getSlotEndDateTime(LocalDate date, String slot) {
        if (date == null) {
            return LocalDateTime.now();
        }
        LocalTime endTime = getSlotEndTime(slot);
        return LocalDateTime.of(date, endTime);
    }

    public static boolean isSlotPassed(LocalDate date, String slot, LocalDateTime now) {
        if (date == null) {
            return false;
        }
        LocalDateTime endDateTime = getSlotEndDateTime(date, slot);
        return now.isAfter(endDateTime);
    }

    public static boolean isValidSlot(String slot) {
        return slot != null && !slot.isBlank();
    }

    /** Delivery slot codes offered at checkout, in chronological order (mirrors UI.SLOTS in ui.js). */
    public static final List<String> KNOWN_SLOTS = List.of("MORNING_09_12", "AFTERNOON_12_15", "EVENING_15_18", "NIGHT_18_21");

    /** True for a checkout slot code or an explicit "HH:mm-HH:mm" range whose end is after its start. */
    public static boolean isKnownSlot(String slot) {
        if (slot == null) {
            return false;
        }
        String s = slot.trim();
        if (KNOWN_SLOTS.contains(s.toUpperCase())) {
            return true;
        }
        if (!s.matches("\\d{2}:\\d{2}-\\d{2}:\\d{2}")) {
            return false;
        }
        try {
            String[] parts = s.split("-");
            return LocalTime.parse(parts[1]).isAfter(LocalTime.parse(parts[0]));
        } catch (Exception e) {
            return false;
        }
    }

    /** Sort key that orders slots chronologically; unknown slots sort after known ones. */
    public static int slotOrder(String slot) {
        int index = slot == null ? -1 : KNOWN_SLOTS.indexOf(slot.trim().toUpperCase());
        return index >= 0 ? index : KNOWN_SLOTS.size();
    }
}
