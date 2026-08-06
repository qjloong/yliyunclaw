package vip.mate.auth.yliyun.runtime;

/** Contract-level authentication or trusted-context failure. */
public class YliyunRuntimeAuthException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;
    private final int httpStatus;
    private final String stage;
    private final String suggestion;
    private final boolean retryable;

    public YliyunRuntimeAuthException(String errorCode, int httpStatus, String message,
                                      String stage, String suggestion, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.stage = stage;
        this.suggestion = suggestion;
        this.retryable = retryable;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getStage() {
        return stage;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
