package io.mutator.samples;
import java.util.regex.Pattern;
public class PatternSample {
    public static boolean matchesDigitsOnly(String input) {
        return Pattern.compile("[0-9]+").matcher(input).matches();
    }
}
