package io.mutator.samples;
public class StringMatcherSample {
    public static boolean matchesDigitsOnly(String input) {
        return input.matches("[0-9]+");
    }
}
