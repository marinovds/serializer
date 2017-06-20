package com.github.marinovds;

import java.io.InputStream;
import java.io.OutputStream;

import com.github.marinovds.exceptions.UnserializableException;

public interface Marshaller {

	String getFormat();

	<T> void serialize(T value, OutputStream stream) throws UnserializableException;

	<T> T deserialize(Class<T> clazz, InputStream stream) throws UnserializableException;
}
