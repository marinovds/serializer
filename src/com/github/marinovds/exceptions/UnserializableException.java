package com.github.marinovds.exceptions;

public class UnserializableException extends Exception {

	private static final long serialVersionUID = 441005079472952975L;

	public UnserializableException() {
		super();
	}

	public UnserializableException(String message) {
		super(message);
	}

	public UnserializableException(Throwable cause) {
		super(cause);
	}

	public UnserializableException(String message, Throwable cause) {
		super(message, cause);
	}
}
