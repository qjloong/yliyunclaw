package vip.mate.auth.yliyun.runtime;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Request wrapper that preserves the exact body bytes used for signature checks. */
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyHttpServletRequest(HttpServletRequest request, int maxBodyBytes) throws IOException {
        super(request);
        if (maxBodyBytes < 1) {
            throw new IllegalArgumentException("maxBodyBytes must be positive");
        }
        byte[] value = request.getInputStream().readNBytes(maxBodyBytes + 1);
        if (value.length > maxBodyBytes) {
            throw new YliyunRuntimeAuthException(
                    "RATE_LIMITED", 413, "Request body exceeds the configured limit",
                    "auth.request_limits",
                    "Reduce the request body size", false);
        }
        this.body = value;
    }

    byte[] bodyBytes() {
        return body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public int read() {
                return input.read();
            }

            @Override
            public int read(byte[] target, int offset, int length) {
                return input.read(target, offset, length);
            }

            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                if (readListener == null) {
                    return;
                }
                try {
                    if (isFinished()) {
                        readListener.onAllDataRead();
                    } else {
                        readListener.onDataAvailable();
                        if (isFinished()) {
                            readListener.onAllDataRead();
                        }
                    }
                } catch (IOException ex) {
                    readListener.onError(ex);
                }
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
