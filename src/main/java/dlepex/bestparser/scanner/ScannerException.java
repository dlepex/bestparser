package dlepex.bestparser.scanner;

/**
 * Created by user on 08/03/15.
 */
public class ScannerException extends RuntimeException {

	public static enum ErrorType {
		IO_ERROR,
		UTF_ERROR,
		BAD_NEWLINE,
	}

	public final ErrorType errorType;

	ScannerException(Throwable cause, ErrorType errorType) {
		super("Scanner stopped: " + errorType, cause);
		this.errorType = errorType;
	}

	ScannerException(ErrorType errorType) {
		this(null, errorType);
	}
}
