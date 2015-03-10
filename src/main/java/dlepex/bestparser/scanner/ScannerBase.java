/*
 * The MIT License
 *
 * Copyright 2015 Denis Lepekhin.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package dlepex.bestparser.scanner;

import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;

/**
 * - Scanner interface inspired by Rob Pike's ivy scanner;
 * - Scanner is mostly allocation free, i.e. if you need to create structure to hold token info -
 *  do it yourself!
 *
 * - instances could be reused if needed
 *
 * T  = token typen (possibly enum)
 * @author Denis Lepekhin
 */

public abstract class ScannerBase<T> {

	protected interface StateFn {
		StateFn apply();
	}

	public static final int EOF = -1;
	private Reader r;
	private final T eofToken, errorToken;

	// codepoints ring buf, contains last bufSzLn2 ^ 2 codepoints of input
	private int[] buf;
	// mask to calculate remainder (x % 2^n == x & (2^n-1))
	private final long bufMask;
	private long posRead;
	private long pos;
	private long tokPos = -1;
	private long linePos;
	private long errorPos;
	private long nextTokPos;
	private long line = 1;

	private @Nullable StateFn stateFn;
	private @Nullable T curToken;
	private @Nullable T expectedToken;
	private Object errorInfo;

	private final TokenBuffer tokenBuf;


	/**
	 * @param bufSzLn2 ln2 of size of "lookahead" buffer
	 */
	protected ScannerBase(Reader reader, int bufSzLn2,
												TokenBuffer tokenBuf,
												T eofToken,
												T errorToken) {
		this.bufMask = (1 << bufSzLn2) - 1;
		this.buf = new int[1 << bufSzLn2];
		this.eofToken = eofToken;
		this.errorToken = errorToken;
		this.tokenBuf = tokenBuf;
		this.r = reader;
		// stateFn points to first scanner state:
		this.stateFn = this::lex;
	}

	public void reset(Reader reader) {
		this.r = reader;
		posRead = 0;
		pos = 0;
		tokPos = -1;
		linePos = 0;
		nextTokPos = 0;
		line = 1;
		stateFn = this::lex;
	}

	public final T scan() throws ScannerException {
		// cleanup:
		curToken = null;
		tokPos = -1;
		tokenBuf.clear();
		expectedToken = null;
		errorInfo = null;

		// optional cleanup extension in child class:
		cleanup();

		do {
			Preconditions.checkState(stateFn != null, "scan beyond EOF");
			stateFn = stateFn.apply();
		} while (curToken == null);

		return curToken;
	}


	public final long line() {
		return line;
	}

	public final long linePos() {
		return linePos;
	}

	// in codepoints, not in chars!
	// is not line relative
	// pos() - linePos() gives line relative position.
	public final long pos() {
		return pos;
	}

	public final long tokenPos() {
		return tokPos;
	}

	public final long errorPos() {
		return errorPos;
	}

	public final T expectedToken() {
		return expectedToken;
	}

	public Object errorInfo() {
		return errorInfo;
	}

	// First state
	protected abstract StateFn lex();


	///
	/// Reading, peeking and backing:
	///

	protected final int peek() {
		int result = next();
		backup();
		return result;
	}

	protected final int next() {
		// pos should never be > posRead
		if (pos == posRead) {
			buf[idx(posRead++)] = readRune();
		}
		return buf[idx(pos++)];
	}

	protected final int nextAppend() {
		return append(next());
	}

	protected final int append(int codepoint) {
		tokenBuf.append(codepoint);
		return codepoint;
	}

	protected final void backup(int times) {
		pos -= times;
	}

	protected final void backup() {
		pos--;
	}



	/**
	 * Every state function must emit no more than one token (or zero)
	 * @param token
	 */
	protected final void emit(T token) {
		this.curToken = token;
		this.tokPos = this.nextTokPos;
		this.nextTokPos = pos;
	}

	protected final void emitIgnore() {
		emit(null);
	}

	protected final /*always returns null*/ StateFn emitEof() {
		emit(eofToken);
		return null;
	}

	protected final void emitNewLine(T token) {
		emit(token);
		line ++;
		linePos = pos;
	}

	protected final void emitNewLine() {
		emitNewLine(null);
	}

	protected final void emitError(T expectedToken, Object errorInfo) {
		this.expectedToken = expectedToken;
		this.errorInfo = errorInfo;
		errorPos = pos;
		emit(errorToken);
	}

	private int idx(long pos) {
		return (int)(pos & bufMask);
	}

	protected void cleanup() {
		// override if needed in children class
	}

	private int readRune() throws ScannerException {
		try {
			int hch = r.read();

			if (hch == '\r') {
				// after \r we expect either \n or eof
				int expected = r.read();
				if (expected != '\n' && expected != -1) {
					throw new ScannerException(ScannerException.ErrorType.BAD_NEWLINE);
				}
			}

			if (hch == EOF) {
				return EOF;
			}
			if (Character.isBmpCodePoint(hch)) {
				return hch;
			}

			if (Character.isHighSurrogate((char) hch)) {
				int lch = r.read();
				if (!Character.isLowSurrogate((char)lch)) {
					throw new ScannerException(ScannerException.ErrorType.UTF_ERROR);
				}
				return Character.toCodePoint((char)hch, (char)lch);
			}

			throw new ScannerException(ScannerException.ErrorType.UTF_ERROR);

		} catch(IOException e) {
			throw new ScannerException(e, ScannerException.ErrorType.IO_ERROR);
		}
	}
}
