package com.github.marinovds;

import java.util.List;
import java.util.Map;

public final class Value {

	private final Object value;
	private final Type type;

	private Value(final Object value, final Type type) {
		this.value = value;
		this.type = type;
	}

	@SuppressWarnings("unchecked")
	public <T> T getValue() {
		return (T) this.value;
	}

	public Type getType() {
		return this.type;
	}

	public static Value createScalar(String value) {
		return new Value(value, Type.SCALAR);
	}

	public static Value createList(List<Value> value) {
		return new Value(value, Type.LIST);
	}

	public static Value createMap(Map<String, Value> value) {
		return new Value(value, Type.MAP);
	}

	public static Value createNull() {
		return new Value(null, Type.NULL);
	}

	@Override
	public String toString() {
		return this.value.toString();
	}

	public enum Type {

		SCALAR, LIST, MAP, NULL;
	}

}
