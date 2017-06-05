package com.github.marinovds;

import java.io.InputStream;
import java.io.OutputStream;

public interface Marshaller {

	String getFormat();

	void serialize(Value value, OutputStream stream);

	Value deserialize(Class<?> clazz, InputStream stream);
}
