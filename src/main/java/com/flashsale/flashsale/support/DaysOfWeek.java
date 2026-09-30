package com.flashsale.flashsale.support;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/** Weekday set ⇄ bitmask (bit 0 = Monday … bit 6 = Sunday), as stored in the database. */
public final class DaysOfWeek {

    public static final short EVERY_DAY = 0b111_1111;

    private DaysOfWeek() {
    }

    public static short toMask(Collection<DayOfWeek> days) {
        int mask = 0;
        for (DayOfWeek day : days) {
            mask |= 1 << (day.getValue() - 1);
        }
        return (short) mask;
    }

    public static Set<DayOfWeek> fromMask(short mask) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (contains(mask, day)) {
                days.add(day);
            }
        }
        return days;
    }

    public static boolean contains(short mask, DayOfWeek day) {
        return (mask & (1 << (day.getValue() - 1))) != 0;
    }
}
