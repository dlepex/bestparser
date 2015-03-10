package dlepex.bestparser.scanner;

import java.io.Reader;
import java.io.StringReader;

/**
 * TODO move to test
 * Created by user on 08/03/15.
 */
public class ScannerSample extends ScannerBase<ScannerSample.TokenType> {


	public static enum TokenType {
		// special lexeme types
		EOF,
		ERROR,

		/**
		 * lexer does not make a difference between
		 * variable, function or named binary operator (like AND, OR)
		 */
		IDENT,
		/**
		 * Any meaningfull delimiting sequence like +, -, >=, ==, =, ",", ";" etc
		 * Lexer doesn't care if it's operator or expression separator etc.
		 */
		DELIM,

		NUMBER,

		STRING,

		OPEN_PAREN, // (
		CLOSE_PAREN; // )
	}

	public ScannerSample(Reader r, TokenBuffer tokenBuffer) {
		super(r, 3, tokenBuffer, TokenType.EOF, TokenType.ERROR);
	}


	@Override protected StateFn lex() {
		int i=0;

		int codepoint = peek();

		if (Character.isSpaceChar(codepoint)) {
			return this::lexSpace;
		}

		switch(codepoint) {
			case '\n':
				next();
				emitNewLine();
				return this::lex;
			case -1:
				return emitEof();
			case '0':case'1':case '2':case '3':case '4':case '5':case '6':case '7':case '8':case '9':
				return this::lexNumber;
			case '\'':
				return this::lexString;
			case '>':case '<':case '=':
				nextAppend();
				if (peek() == '=') {
					nextAppend();
				}
				emit(TokenType.DELIM);
				return this::lex;
			case'+':case'-':case'*':case'/':case',':case';':
				nextAppend();
				emit(TokenType.DELIM);
				return this::lex;
			case '(':
				nextAppend();
				emit(TokenType.OPEN_PAREN);
				return this::lex;
			case ')':
				nextAppend();
				emit(TokenType.CLOSE_PAREN);
				return this::lex;
			default:
				return this::lexIdent;
		}

	}

	protected StateFn lexSpace() {
		while(Character.isSpaceChar(peek())) {
			next();
		}
		emitIgnore();
		return this::lex;
	}

	protected StateFn lexNumber() {
		//simplified: only integers;
		int p;
		while((p=peek()) >= '0' && p <= '9') {
			nextAppend();
		}
		if (!Character.isJavaIdentifierPart(p)) {
			emit(TokenType.NUMBER);
		} else {
			emitError(null, "Unexpected symbol after number");
		}

		return this::lex;
	}

	protected StateFn lexString() {
		next();
		int p;
		while((p = peek()) != '\'' && p != EOF) {
			if (p != '\\') {
				nextAppend();
			} else {
				next(); // ignore '\'
				p = peek(); // peek what is next;
				if (p == EOF) {
					break;
				}
				switch(p) {
					case '\'':case'\n':case'\\':
						nextAppend();
						break;
				}
			}
		}
		if (p != EOF) {
			next();
			emit(TokenType.STRING);
		} else {
			emitError(TokenType.STRING, "Eof found");
		}
		return this::lex;
	}

	protected StateFn lexIdent() {
		int p = peek();
		if (!Character.isJavaIdentifierStart(p)) {
			emitError(TokenType.IDENT, "Bad identifier char");
			next();
			return this::lex;
		}
		while(Character.isJavaIdentifierPart(peek())) {
			nextAppend();
		}
		emit(TokenType.IDENT);
		return this::lex;
	}


	public static void main(String[] args) {

		String text = "12c('©'+b)*c+1234 c \n ==== 'He©llo \\' \\\n world'";


		StringReader reader = new StringReader(text);
		StringBuilder tbuf = new StringBuilder();
		ScannerSample scanner = new ScannerSample(reader, TokenBuffer.wrapStringBuilder(tbuf));


		TokenType tok;

		while((tok = scanner.scan()) != TokenType.EOF) {
			System.out.println(tok + "(" + scanner.line() + ", " + (scanner.tokenPos() - scanner.linePos()) + ")  {" +

					(tok != TokenType.ERROR ? tbuf : scanner.errorInfo()) + "}" +
					(tok == TokenType.ERROR ? scanner.expectedToken() : ""));

		}





	}

}
