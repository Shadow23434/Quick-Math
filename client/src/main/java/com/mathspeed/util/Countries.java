package com.mathspeed.util;

import java.util.*;

public class Countries {
    private static final Map<String, String> NAME_TO_CODE = new TreeMap<>();
    private static final Map<String, String> CODE_TO_NAME = new HashMap<>();

    static {
        String[] isos = java.util.Locale.getISOCountries();
        for (String iso : isos) {
            java.util.Locale locale = new java.util.Locale("", iso);
            String name = locale.getDisplayCountry(java.util.Locale.ENGLISH);
            if (name != null && !name.isEmpty()) {
                NAME_TO_CODE.put(name, iso);
                CODE_TO_NAME.put(iso, name);
            }
        }
    }

    public static List<String> getCountryNames() {
        return new ArrayList<>(NAME_TO_CODE.keySet());
    }

    public static List<String> getCountryCodesSortedByName() {
        List<String> codes = new ArrayList<>();
        for (String name : NAME_TO_CODE.keySet()) {
            codes.add(NAME_TO_CODE.get(name));
        }
        return codes;
    }

    public static String getCodeForName(String name) {
        return NAME_TO_CODE.get(name);
    }

    public static String getNameForCode(String code) {
        return CODE_TO_NAME.get(code);
    }
}

