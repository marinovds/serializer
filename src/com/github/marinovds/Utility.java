package com.github.marinovds;

public class Utility {

	public static final String MSG_UTILITY_INSTANTIATION = "Utility classes cannot be instantiated";

	private Utility() {
		throw new UnsupportedOperationException(MSG_UTILITY_INSTANTIATION);
	}

	public static void throwUnchecked(final Exception ex) {
		Utility.<RuntimeException>throwsUnchecked(ex);
	}

	private static <T extends Exception> void throwsUnchecked(final Exception toThrow) throws T {
		throw (T) toThrow;
	}

	public static void validateInput(Object object, String errorMessage) {
		if (object == null) {
			throw new IllegalArgumentException(errorMessage);
		}
	}

}
