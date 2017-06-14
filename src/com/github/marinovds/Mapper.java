package com.github.marinovds;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.marinovds.Value.Type;
import com.github.marinovds.annotations.Entry;
import com.github.marinovds.annotations.Ignore;
import com.github.marinovds.annotations.Root;
import com.github.marinovds.exceptions.UnconvertableException;

final class Mapper {

	private Mapper() {
		throw new UnsupportedOperationException("Utility classes cannot be instantiated");
	}

	public static Value toValue(Object object) {
		Type type = getValueType(object);
		switch (type) {
			case MAP:
				if (object instanceof Map) {
					return createMapValue(object);
				}
				return createRootBeanValue(object);
			case LIST:
				return createListValue(object);
			case SCALAR:
				return Value.createScalar(String.valueOf(object));
			case NULL:
				return Value.createNull();
			default:
				return Value.createNull();
		}
	}

	private static Value createRootBeanValue(Object object) {
		Class<?> clazz = object.getClass();
		String rootName = getRootName(clazz);
		Value rootValue = getObjectValue(clazz, object);
		return Value.createMap(Collections.singletonMap(rootName, rootValue));
	}

	private static String getRootName(Class<?> clazz) {
		Root root = clazz.getAnnotation(Root.class);
		if (root != null) {
			return root.value();
		}
		return clazz.getSimpleName();
	}

	private static Value getObjectValue(Class<?> clazz, Object object) {
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
			return entry.value();
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
		if (isNull(value)) {
			return Type.NULL;
		}
		if (isScalar(value)) {
			return Type.SCALAR;
		}
		if (isList(value)) {
			return Type.LIST;
		}
		return Type.MAP;
	}

	private static boolean isNull(Object value) {
		if (value == null) {
			return true;
		}
		return false;
	}

	private static boolean isScalar(Object value) {
		if (value instanceof Character) {
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
			case NULL:
				return Value.createNull();
			default:
				return Value.createNull();
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
		return getObjectValue(value.getClass(), value);
	}

	private static Map<String, Value> handleMap(Map<?, ?> map) {
		Map<String, Value> retval = new HashMap<>();
		for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
			Object key = entry.getKey();
			if (!isScalar(key)) {
				throw new UnconvertableException("Non scalar Map keys cannot be serialised");
			}
			retval.put(String.valueOf(key), objectToValue(entry.getValue()));
		}
		return retval;
	}

	private static Value objectToValue(Object object) {
		Type type = getValueType(object);
		return correctValue(object, type);
	}

	//////////////////////////////////////////////////////////////////////////

	public static <T> T toObject(Class<T> clazz, Value value) {
		Type type = value.getType();
		switch (type) {
			case MAP:
				return null; // TODO
			case LIST:
				return createCollectionObject(clazz, value);
			case SCALAR:
				return createScalarObject(clazz, value);
			case NULL:
				return null;
			default:
				return null;
		}
	}

	private static <T> T createScalarObject(Class<T> clazz, Value value) {
		ConcreteType type = getConcreteType(clazz);
		if (!isScalarType(type)) {
			throw new UnconvertableException(clazz.getName() + " is not compatible for type " + Type.SCALAR);
		}
		return createScalarObject(type, (String) value.getValue());
	}

	@SuppressWarnings("unchecked")
	private static <T> T createScalarObject(ConcreteType type, String value) {
		switch (type) {
			case BOOLEAN:
				return (T) Boolean.valueOf(value);
			case BYTE:
				return (T) Byte.valueOf(value);
			case INTEGER:
				return (T) Integer.valueOf(value);
			case LONG:
				return (T) Long.valueOf(value);
			case SHORT:
				return (T) Short.valueOf(value);
			case FLOAT:
				return (T) Float.valueOf(value);
			case DOUBLE:
				return (T) Double.valueOf(value);
			case CHARACTER:
				return (T) stringToCharacter(value);
			case STRING:
				return (T) value;
			default:
				return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T createCollectionObject(Class<T> clazz, Value value) {
		ConcreteType type = getConcreteType(clazz);
		if (!isList(type)) {
			throw new UnconvertableException(clazz.getName() + " is not compatible for type " + Type.LIST);
		}
		return createCollectionObject(type, clazz, (List<Value>) value.getValue());
	}

	@SuppressWarnings("unchecked")
	private static <T> T createCollectionObject(ConcreteType type, Class<T> clazz, List<Value> value) {
		Class<?> genericType = null;
		Class<?> componentType = null;
		switch (type) {
			case ARRAY:
				componentType = clazz.getComponentType();
				return (T) createArrayObject(componentType, value);
			case LIST:
				genericType = getGenericType(clazz);
				return (T) createListObject(genericType, value);
			case SET:
				genericType = getGenericType(clazz);
				return (T) createSetObject(genericType, value);
			default:
				return null;
		}
	}

	private static Class<?> getGenericType(Class<?> clazz) {
		// TODO
		return null;
	}

	private static Object createArrayObject(Class<?> componentType, List<Value> value) {
		int length = value.size();
		Object retval = Array.newInstance(componentType, length);
		for (int i = 0; i < length; i++) {
			Value element = value.get(i);
			Array.set(retval, i, toObject(componentType, element));
		}
		return retval;
	}

	private static Object createListObject(Class<?> clazz, List<Value> value) {
		List<Object> retval = new ArrayList<>();
		value.forEach(element -> {
			Object object = toObject(clazz, element);
			retval.add(object);
		});
		return retval;
	}

	private static Object createSetObject(Class<?> clazz, List<Value> value) {
		Set<Object> retval = new HashSet<>();
		value.forEach(element -> {
			Object object = toObject(clazz, element);
			retval.add(object);
		});
		return retval;
	}

	private static ConcreteType getConcreteType(Class<?> clazz) {
		if (clazz.isAssignableFrom(Boolean.class) || clazz.isAssignableFrom(boolean.class)) {
			return ConcreteType.BOOLEAN;
		}
		if (clazz.isAssignableFrom(Byte.class) || clazz.isAssignableFrom(byte.class)) {
			return ConcreteType.BYTE;
		}
		if (clazz.isAssignableFrom(Integer.class) || clazz.isAssignableFrom(int.class)) {
			return ConcreteType.INTEGER;
		}
		if (clazz.isAssignableFrom(Long.class) || clazz.isAssignableFrom(long.class)) {
			return ConcreteType.LONG;
		}
		if (clazz.isAssignableFrom(Short.class) || clazz.isAssignableFrom(short.class)) {
			return ConcreteType.SHORT;
		}
		if (clazz.isAssignableFrom(Float.class) || clazz.isAssignableFrom(float.class)) {
			return ConcreteType.FLOAT;
		}
		if (clazz.isAssignableFrom(Double.class) || clazz.isAssignableFrom(double.class)) {
			return ConcreteType.DOUBLE;
		}
		if (clazz.isAssignableFrom(Character.class) || clazz.isAssignableFrom(char.class)) {
			return ConcreteType.CHARACTER;
		}
		if (clazz.isAssignableFrom(String.class)) {
			return ConcreteType.STRING;
		}
		if (clazz.isArray()) {
			return ConcreteType.ARRAY;
		}
		if (clazz.isAssignableFrom(Set.class)) {
			return ConcreteType.SET;
		}
		if (clazz.isAssignableFrom(List.class) || clazz.isAssignableFrom(Collection.class)) {
			return ConcreteType.LIST;
		}
		if (clazz.isAssignableFrom(Map.class)) {
			return ConcreteType.MAP;
		}
		return ConcreteType.OBJECT;
	}

	private static boolean isScalarType(ConcreteType type) {
		if (type == ConcreteType.BOOLEAN) {
			return true;
		}
		if (type == ConcreteType.BYTE) {
			return true;
		}
		if (type == ConcreteType.INTEGER) {
			return true;
		}
		if (type == ConcreteType.LONG) {
			return true;
		}
		if (type == ConcreteType.SHORT) {
			return true;
		}
		if (type == ConcreteType.FLOAT) {
			return true;
		}
		if (type == ConcreteType.DOUBLE) {
			return true;
		}
		if (type == ConcreteType.CHARACTER) {
			return true;
		}
		if (type == ConcreteType.STRING) {
			return true;
		}
		return false;
	}

	private static Character stringToCharacter(String value) {
		if (value.length() == 0) {
			throw new UnconvertableException("Cannot convert empty String to char");
		}
		String retval = value.trim();
		int length = retval.length();
		if (length > 1) {
			throw new UnconvertableException("Cannot convert String to char");
		}
		if (length == 0) {
			return Character.valueOf(' ');
		}
		return Character.valueOf(retval.charAt(0));
	}

	private static enum ConcreteType {
		BOOLEAN, BYTE, INTEGER, LONG, SHORT, FLOAT, DOUBLE, CHARACTER, STRING, ARRAY, LIST, SET, MAP, OBJECT;
	}
}
