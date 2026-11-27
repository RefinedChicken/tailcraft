package io.github.refinedchicken.tailcraft;

import java.util.Base64;

/**
 * Recognizes Tailcat addresses ({@code tc} + base64url-encoded CBOR map) without decoding them
 * fully. The Go helper does the authoritative parse; this check only decides whether an address
 * typed into Minecraft takes the Tailcraft path instead of normal DNS.
 *
 * <p>Real hostnames can't match: a match has no dots or colons, uses only base64url characters,
 * is far longer than a DNS label may be, and decodes to a CBOR map header.
 */
public final class TailcraftAddress {
    /** Shortest real address: three 32-byte keys plus CBOR framing is well over this. */
    private static final int MIN_LENGTH = 80;
    public static final int MAX_LENGTH = 1024;

    private TailcraftAddress() {}

    public static boolean isAddress(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.length() < MIN_LENGTH || s.length() > MAX_LENGTH || !s.startsWith("tc")) return false;
        for (int i = 2; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok) return false;
        }
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(s.substring(2));
        } catch (IllegalArgumentException e) {
            return false;
        }
        // CBOR major type 5 (map) lives in the top three bits of the first byte.
        return decoded.length > 0 && (decoded[0] & 0xE0) == 0xA0;
    }
}
