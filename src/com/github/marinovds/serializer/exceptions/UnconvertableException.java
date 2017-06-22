package com.github.marinovds.serializer.exceptions;

public class UnconvertableException extends RuntimeException {

	private static final long serialVersionUID = 4619985508193458256L;

	public UnconvertableException() {
		super();
	}

	public UnconvertableException(Throwable cause) {
		super(cause);
	}

	public UnconvertableException(String message) {
		super(message);
	}

	public UnconvertableException(String message, Throwable cause) {
		super(message, cause);
	}
}
