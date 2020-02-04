package unimelb.bitbox.util;

public class Tuple<T, U> {
	private final T fstElem;
	private final U sndElem;

	public Tuple(T fstElem, U sndElem) {
		this.fstElem = fstElem;
		this.sndElem = sndElem;
	}

	public T getFstElem() { return fstElem; }
	public U getSndElem() { return sndElem; }
}
