package dlepex.bestparser.scanner;


public abstract class TokenBuffer {

	public abstract void append(int utfCodepoint);

	public abstract void clear();



	public static TokenBuffer wrapStringBuilder(StringBuilder sb) {
		return new TokenBuffer() {
			@Override
			public void append(int utfCodepoint) {
				sb.appendCodePoint(utfCodepoint);
			}

			@Override
			public void clear() {
				sb.setLength(0);
			}
		};
	}

}
