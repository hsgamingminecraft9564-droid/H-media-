import java.net.URI;
import java.nio.charset.StandardCharsets;

// Line-for-line port of WebPolicy.kt, so the link rules run on a real JVM with java.net.URI.
public class WebPolicyCheck {
    static final int MAX_LENGTH = 8192;
    static final String HEX = "0123456789ABCDEF";
    static final String KEEP = "-._~:/?#&=+@!$'()*,;";

    static boolean isHex(char c) { return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'); }

    static String encodeForUri(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 16);
        int i = 0;
        while (i < raw.length()) {
            int cp = raw.codePointAt(i);
            char ch = raw.charAt(i);
            if (ch == '%' && i + 2 < raw.length() && isHex(raw.charAt(i + 1)) && isHex(raw.charAt(i + 2))) {
                out.append('%');
            } else if (cp < 128 && (Character.isLetterOrDigit(cp) || KEEP.indexOf(ch) >= 0)) {
                out.append(ch);
            } else {
                byte[] bytes = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    out.append('%').append(HEX.charAt((b >> 4) & 0xF)).append(HEX.charAt(b & 0xF));
                }
            }
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    static boolean isAllowedPage(String raw) {
        if (raw == null || raw.trim().isEmpty() || raw.length() > MAX_LENGTH) return false;
        URI uri;
        try { uri = new URI(encodeForUri(raw.trim())); } catch (Exception e) { return false; }
        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase();
        if (scheme == null) return false;
        if (!scheme.equals("http") && !scheme.equals("https")) return false;
        if (uri.getHost() == null || uri.getHost().isEmpty()) return false;
        if (uri.getRawUserInfo() != null) return false;
        return true;
    }

    static int failures = 0;
    static void check(String name, boolean expected, boolean actual) {
        if (expected != actual) { failures++; System.out.println("FAIL " + name + " expected " + expected); }
        else System.out.println("ok   " + name);
    }

    public static void main(String[] a) {
        // webAddressesMayOpen
        check("https page with query", true, isAllowedPage("https://example.com/page?x=1"));
        check("http plain", true, isAllowedPage("http://example.org"));
        check("upper-case scheme", true, isAllowedPage("HTTPS://Example.com"));
        // linksWithSpecialCharactersOpen
        check("pipe, brackets, caret, braces", true, isAllowedPage("https://example.com/search?q=a|b[1]^{x}&lang=ne"));
        check("spaces in path", true, isAllowedPage("https://example.com/path with spaces/page"));
        check("devanagari path and query", true, isAllowedPage("https://example.com/\u0928\u0947\u092a\u093e\u0932\u0940?q=\u090a"));
        check("lone percent sign", true, isAllowedPage("https://example.com/100%"));
        check("existing percent encoding", true, isAllowedPage("https://example.com/%E0%A4%A8"));
        // longButReasonableLinksOpen
        check("5000-char path", true, isAllowedPage("https://example.com/" + "a".repeat(5000)));
        // appAndScriptSchemesAreRefused
        String[] bad = {"javascript:alert(1)", "intent://example#Intent;end", "market://details?id=x",
            "file:///sdcard/secret.txt", "content://media/external/video/1", "data:text/html,hi"};
        for (String u : bad) check("refused scheme: " + u, false, isAllowedPage(u));
        // linksThatHideTheirDestinationAreRefused
        check("userinfo hides host", false, isAllowedPage("https://bank.example@evil.example/login"));
        check("user:pass", false, isAllowedPage("https://user:pass@example.com"));
        // malformedOrOversizedAddressesAreRefused
        check("null", false, isAllowedPage(null));
        check("empty", false, isAllowedPage(""));
        check("bare scheme", false, isAllowedPage("https://"));
        check("space in host", false, isAllowedPage("https://exa mple.com"));
        check("9000-char path", false, isAllowedPage("https://example.com/" + "a".repeat(9000)));
        System.out.println(failures == 0 ? "ALL WEB POLICY CASES PASS" : failures + " FAILURES");
    }
}
