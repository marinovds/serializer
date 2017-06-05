package com.github.marinovds;

import java.io.InputStream;
import java.io.OutputStream;

class MarshallerImpl implements Marshaller {

	private final String format;
	private final Serializer serializer;

	public MarshallerImpl(String format, Serializer serializer) {
		this.format = format;
		this.serializer = serializer;
	}

	public String getFormat() {
		return this.format;
	}

	@Override
	public void serialize(Value value, OutputStream stream) {
		this.serializer.serialize(value, stream);
	}

	@Override
	public Value deserialize(Class<?> clazz, InputStream stream) {
		return this.serializer.deserialize(clazz, stream);
	}

}
