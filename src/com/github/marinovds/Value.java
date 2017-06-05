package com.github.marinovds;

import java.util.List;
import java.util.Map;

public class Value {

	private final Object value;
	private final ValueType type;

	private Value(Object value, ValueType type) {
		this.value = value;
		this.type = type;
	}

	public Object getValue() {
		return this.value;
	}

	public ValueType getType() {
		return this.type;
	}

	public static Value createScalar(String value) {
		return new Value(value, ValueType.SCALAR);
	}

	public static Value createList(List<Value> value) {
		return new Value(value, ValueType.LIST);
	}

	public static Value createMap(Map<String, Value> value) {
		return new Value(value, ValueType.MAP);
	}

	public enum ValueType {

		SCALAR, LIST, MAP;
	}

}
