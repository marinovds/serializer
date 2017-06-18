package com.github.marinovds;

import java.io.InputStream;
import java.io.OutputStream;

import com.github.marinovds.exceptions.UnserializableException;

public interface Serializer {

	void serialize(Value value, OutputStream stream) throws UnserializableException;

	Value deserialize(Class<?> clazz, InputStream stream) throws UnserializableException;
}
