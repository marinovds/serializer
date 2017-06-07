package com.github.marinovds;

import java.io.InputStream;
import java.io.OutputStream;

public interface Marshaller {

	String getFormat();

	void serialize(Object value, OutputStream stream);

	<T> T deserialize(Class<T> clazz, InputStream stream);
}
