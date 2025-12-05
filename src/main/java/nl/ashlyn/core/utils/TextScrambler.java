package nl.ashlyn.core.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by Ashlyn on 05/12/2025
 */
public class TextScrambler {
    public static String scrambleLetters(String msg) {
        if (msg == null || msg.isEmpty()) return msg;

        List<Integer> letterIndices = new ArrayList<>();
        for (int i = 0; i < msg.length(); i++) {
            if (Character.isLetter(msg.charAt(i))) {
                letterIndices.add(i);
            }
        }

        if (letterIndices.size() < 2) return msg;

        double fraction = 0.30 + ThreadLocalRandom.current().nextDouble() * 0.15;
        int toScramble = Math.max(1, (int) Math.round(letterIndices.size() * fraction));

        Collections.shuffle(letterIndices, ThreadLocalRandom.current());
        List<Integer> positions = new ArrayList<>(letterIndices.subList(0, toScramble));

        char[] chars = msg.toCharArray();

        List<Character> letters = new ArrayList<>(positions.size());
        for (int pos : positions) {
            letters.add(chars[pos]);
        }
        Collections.shuffle(letters, ThreadLocalRandom.current());

        for (int i = 0; i < positions.size(); i++) {
            int pos = positions.get(i);
            char src = letters.get(i);
            chars[pos] = Character.isUpperCase(chars[pos]) ? Character.toUpperCase(src) : Character.toLowerCase(src);
        }

        return new String(chars);
    }

}