package com.github.marinovds;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.marinovds.Value.Type;
import com.github.marinovds.annotations.Entry;
import com.github.marinovds.annotations.Ignore;
import com.github.marinovds.annotations.Root;

final class Mapper {

	private Mapper() {
		throw new UnsupportedOperationException("Utility classes cannot be instantiated");
	}

	public static Value toValue(Object object) {
		Class<?> clazz = object.getClass();
		String rootName = getRootName(clazz);
		Value rootValue = getRootValue(clazz, object);
		return Value.createMap(Collections.singletonMap(rootName, rootValue));
	}

	private static String getRootName(Class<?> clazz) {
		Root root = clazz.getAnnotation(Root.class);
		if (root != null) {
			return root.name();
		}
		return clazz.getSimpleName();
	}

	private static Value getRootValue(Class<?> clazz, Object object) {
		Stream<Field> fields = getSerializableFields(clazz.getDeclaredFields());
		Map<String, Value> retval = fields
				.collect(Collectors.toMap(Mapper::getEntryName, f -> getEntryValue(f, object)));
		return Value.createMap(retval);
	}

	private static Stream<Field> getSerializableFields(Field[] fields) {
		return Arrays.stream(fields).filter(Mapper::shouldSerialize);
	}

	private static boolean shouldSerialize(Field field) {
		Ignore ignore = field.getAnnotation(Ignore.class);
		if (ignore != null) {
			return false;
		}
		return !Modifier.isTransient(field.getModifiers());
	}

	private static String getEntryName(Field field) {
		Entry entry = field.getAnnotation(Entry.class);
		if (entry != null) {
			return entry.name();
		}
		return field.getName();
	}

	private static Value getEntryValue(Field field, Object object) {
		try {
			Object value = field.get(object);
			return objectToValue(value);
		} catch (IllegalArgumentException e) {
			Utility.throwUnchecked(e);
		} catch (IllegalAccessException e) {
			Utility.throwUnchecked(e);
		}
		// Cannot happen
		return null;
	}

	private static Type getValueType(Object value) {
		if (isScalar(value)) {
			return Type.SCALAR;
		}
		if (isList(value)) {
			return Type.LIST;
		}
		return Type.MAP;
	}

	private static boolean isScalar(Object value) {
		if (value == null) {
			return true;
		}
		if (value instanceof String) {
			return true;
		}
		if (value instanceof Boolean) {
			return true;
		}
		if (value instanceof Number) {
			return true;
		}
		return false;

	}

	private static boolean isList(Object value) {
		if (value instanceof Collection) {
			return true;
		}
		if (value.getClass().isArray()) {
			return true;
		}
		return false;
	}

	private static Value correctValue(Object value, Type type) {
		switch (type) {
			case SCALAR:
				return Value.createScalar(String.valueOf(value));
			case LIST:
				return createListValue(value);
			case MAP:
				return createMapValue(value);
			default:
				// Unreachable
				return null;
		}
	}

	private static Value createListValue(Object object) {
		List<Value> retval = Collections.emptyList();
		if (object instanceof Collection) {
			retval = handleCollection((Collection<?>) object);
		}
		if (object.getClass().isArray()) {
			retval = handleArray(object);
		}
		return Value.createList(retval);
	}

	private static List<Value> handleCollection(Collection<?> collection) {
		List<Value> retval = new ArrayList<>();
		collection.stream().forEach(item -> {
			Value value = objectToValue(item);
			retval.add(value);
		});
		return retval;
	}

	private static List<Value> handleArray(Object object) {
		List<Value> retval = new ArrayList<>();
		int length = Array.getLength(object);
		for (int i = 0; i < length; i++) {
			Object item = Array.get(object, i);
			Value value = objectToValue(item);
			retval.add(value);
		}
		return retval;
	}

	private static Value createMapValue(Object value) {
		if (value instanceof Map) {
			return Value.createMap(handleMap((Map<?, ?>) value));
		}
		return getRootValue(value.getClass(), value);
	}

	private static Map<String, Value> handleMap(Map<?, ?> map) {
		Map<String, Value> retval = new HashMap<>();
		for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
			Object key = entry.getKey();
			if (!isScalar(key)) {
				throw new UnsupportedOperationException("Non scalar Map keys cannot be serialised");
			}
			retval.put(String.valueOf(key), objectToValue(entry.getValue()));
		}
		return retval;
	}

	private static Value objectToValue(Object object) {
		Type type = getValueType(object);
		return correctValue(object, type);
	}

	public static <T> T toObject(Class<T> clazz, Value value) {
		return null;
	}

}
