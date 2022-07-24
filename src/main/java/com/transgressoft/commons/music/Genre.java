package com.transgressoft.commons.music;

/**
 * @author Octavio Calleya
 */
public enum Genre {

    UNDEFINED;

    public static Genre parseGenre(String value) {
        return UNDEFINED;
    }

    public String capitalize() {
        String replaced = this.name().replace('_', ' ');

        char[] capitalized = new char[replaced.length()];
        capitalized[0] = Character.toTitleCase(replaced.charAt(0));
        for (int c = 1; c < replaced.toCharArray().length; c++)
            if (replaced.charAt(c - 1) == ' ' || replaced.charAt(c - 1) == ',')
                capitalized[c] = Character.toTitleCase(replaced.charAt(c));

        return new String(capitalized);
    }
}
