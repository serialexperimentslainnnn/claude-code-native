package dev.lain.claudejb.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class Frames {

    public static final int MAX_FRAME_BYTES = 64 * 1024 * 1024;
    private static final int MAX_HEADER_DIGITS = 10;

    private Frames() {
    }

    public static void write(OutputStream out, String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        out.write((payload.length + "\n").getBytes(StandardCharsets.US_ASCII));
        out.write(payload);
        out.flush();
    }

    public static String read(InputStream in) throws IOException {
        long length = 0;
        int digits = 0;
        while (true) {
            int b = in.read();
            if (b < 0) return digits == 0 ? null : fail("stream ended inside a frame header");
            if (b == '\n') break;
            if (b < '0' || b > '9' || digits == MAX_HEADER_DIGITS) return fail("malformed frame header");
            length = length * 10 + (b - '0');
            digits++;
            if (length > MAX_FRAME_BYTES) return fail("frame exceeds the ceiling of " + MAX_FRAME_BYTES + " bytes");
        }
        if (digits == 0) return fail("malformed frame header");
        return body(in, (int) length);
    }

    private static String body(InputStream in, int length) throws IOException {
        byte[] payload = in.readNBytes(length);
        if (payload.length != length) return fail("stream ended inside a frame body");
        return new String(payload, StandardCharsets.UTF_8);
    }

    private static String fail(String message) throws IOException {
        throw new IOException(message);
    }
}
