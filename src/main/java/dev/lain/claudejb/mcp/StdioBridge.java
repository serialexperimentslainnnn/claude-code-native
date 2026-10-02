package dev.lain.claudejb.mcp;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StdioBridge {

    public static final String TOKEN_KEY = "dev.lain.claudejb/token";
    public static final String TOKEN_FILE = "token";
    private static final int PARSE_ERROR = -32700;
    private static final int INTERNAL_ERROR = -32603;

    private final Path socket;
    private final Path tokenFile;
    private final PrintStream stdout;

    StdioBridge(Path socket, PrintStream stdout) {
        this.socket = socket;
        this.tokenFile = socket.resolveSibling(TOKEN_FILE);
        this.stdout = stdout;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: StdioBridge <socket>");
            System.exit(2);
        }
        PrintStream stdout = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        new StdioBridge(Path.of(args[0]), stdout).pump(System.in);
    }

    void pump(InputStream stdin) throws IOException {
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(socket));
            OutputStream toPlugin = Channels.newOutputStream(channel);
            InputStream fromPlugin = new BufferedInputStream(Channels.newInputStream(channel));
            Thread replies = new Thread(() -> relayReplies(fromPlugin, channel), "mcp-replies");
            replies.setDaemon(true);
            replies.start();
            relayRequests(stdin, toPlugin);
            channel.shutdownOutput();
            waitFor(replies);
        }
    }

    private void relayRequests(InputStream stdin, OutputStream toPlugin) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            Object message;
            try {
                message = Json.parse(line);
            } catch (IllegalArgumentException e) {
                emit(Json.write(error(PARSE_ERROR, "Parse error")));
                continue;
            }
            synchronized (toPlugin) {
                Frames.write(toPlugin, Toon.encode(withToken(message)));
            }
        }
    }

    void relayReplies(InputStream fromPlugin, SocketChannel channel) {
        try {
            String frame;
            while ((frame = Frames.read(fromPlugin)) != null) {
                relayReply(frame);
            }
        } catch (IOException e) {
            System.err.println("StdioBridge: " + e.getMessage());
            closeQuietly(channel);
        }
    }

    private void relayReply(String frame) {
        String line;
        try {
            line = Json.write(Toon.decode(frame));
        } catch (RuntimeException e) {
            System.err.println("StdioBridge: unreadable reply: " + e.getMessage());
            line = Json.write(error(INTERNAL_ERROR, "unreadable reply from the IDE"));
        }
        emit(line);
    }

    private static void closeQuietly(SocketChannel channel) {
        if (channel == null) return;
        try {
            channel.close();
        } catch (IOException e) {
            System.err.println("StdioBridge: " + e.getMessage());
        }
    }

    private Object withToken(Object message) {
        if (!(message instanceof Map)) return message;
        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) message;
        if (!request.containsKey("method")) return request;
        Object params = request.get("params");
        Map<String, Object> paramsMap = params instanceof Map ? cast(params) : new LinkedHashMap<>();
        Object meta = paramsMap.get("_meta");
        Map<String, Object> metaMap = meta instanceof Map ? cast(meta) : new LinkedHashMap<>();
        String token = readToken();
        if (token != null) metaMap.put(TOKEN_KEY, token);
        paramsMap.put("_meta", metaMap);
        request.put("params", paramsMap);
        return request;
    }

    private String readToken() {
        try {
            return Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
    }

    private void emit(String line) {
        synchronized (stdout) {
            stdout.print(line);
            stdout.print('\n');
            stdout.flush();
        }
    }

    private static Map<String, Object> error(int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", null);
        response.put("error", error);
        return response;
    }

    private static void waitFor(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }
}
