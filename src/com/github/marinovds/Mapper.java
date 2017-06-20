package com.github.marinovds;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
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
					throw new UnconvertableException("Cannot convert maps. Pass a Bean instead");
				}
				return createRootBeanValue(object);
			case LIST:
				throw new UnconvertableException("Cannot convert collections or arrays. Pass a Bean instead");
			case SCALAR:
				throw new UnconvertableException("Cannot convert scalars. Pass a Bean instead");
			case NULL:
				return Value.createNull();
			default:
				// Cannot happen
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
		field.setAccessible(true);
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
		} catch (IllegalArgumentException | IllegalAccessException e) {
			Utility.throwUnchecked(e);
		}
		return Value.createNull();
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
				if (clazz.isAssignableFrom(Map.class)) {
					throw new UnconvertableException("Cannot be converted to Map. Only beans can be converted");
				}
				return createBeanObject(clazz, value); //
			case LIST:
				throw new UnconvertableException(
						"Cannot be converted to collection or array. Only beans can be converted");
			case SCALAR:
				throw new UnconvertableException("Cannot be converted to scalar. Only beans can be converted");
			case NULL:
				return null;
			default:
				// Cannot happen
				throw new UnconvertableException();
		}
	}

	private static <T> T createBeanObject(Class<T> clazz, Value value) {
		try {
			Constructor<T> constructor = getConstructor(clazz);
			T instance = constructor.newInstance();
			setValues(instance, value.getValue());
			return instance;
		} catch (SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			Utility.throwUnchecked(e);
			// Cannot happen
			return null;
		}
	}

	private static void checkValueType(Value value, Type expectedType) {
		Type actualType = value.getType();
		if (actualType != expectedType) {
			throw new UnconvertableException(
					"Cannot convert types. Expected " + expectedType + " but " + actualType + " was present");
		}
	}

	private static <T> Constructor<T> getConstructor(Class<T> clazz) {
		try {
			return clazz.getConstructor();
		} catch (NoSuchMethodException | SecurityException e) {
			Utility.throwUnchecked(e);
			// Cannot happen
			return null;
		}
	}

	private static <T> void setValues(T instance, Map<String, Value> values) {
		Class<?> clazz = instance.getClass();
		values.forEach((fieldName, fieldValue) -> {
			try {
				Field field = getField(clazz, fieldName);
				ResolvedType type = ResolvedType.create(field);
				Object object = valueToObject(type, fieldValue);
				field.set(instance, object);
			} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
				Utility.throwUnchecked(e);
			}
		});

	}

	private static Field getField(Class<?> clazz, String fieldName) throws NoSuchFieldException, SecurityException {
		Field retval = clazz.getDeclaredField(fieldName);
		retval.setAccessible(true);
		return retval;
	}

	private static Object valueToObject(ResolvedType type, Value value) {
		if (value == null) {
			return null;
		}
		ConcreteType concreteType = getConcreteType(type.getType());
		if (isScalarType(concreteType)) {
			checkValueType(value, Type.SCALAR);
			return createScalarObject(concreteType, (String) value.getValue());
		}
		if (isListType(concreteType)) {
			checkValueType(value, Type.LIST);
			return createCollectionObject(concreteType, type, value.getValue());
		}
		checkValueType(value, Type.MAP);
		return createMapObject(concreteType, type, value);
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
			case ARRAY:
			case LIST:
			case MAP:
			case OBJECT:
			case SET:
			default:
				// Cannot happen because of checks
				throw new UnconvertableException();
		}
	}

	private static boolean isListType(ConcreteType type) {
		switch (type) {
			case ARRAY:
			case LIST:
			case SET:
				return true;
			case BOOLEAN:
			case BYTE:
			case CHARACTER:
			case DOUBLE:
			case FLOAT:
			case INTEGER:
			case LONG:
			case MAP:
			case OBJECT:
			case SHORT:
			case STRING:
			default:
				return false;
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T createCollectionObject(ConcreteType type, ResolvedType resolvedType, List<Value> value) {
		switch (type) {
			case ARRAY:
				return (T) createArrayObject(resolvedType, value);
			case LIST:
				return (T) createListObject(resolvedType, value);
			case SET:
				return (T) createSetObject(resolvedType, value);
			case BOOLEAN:
			case BYTE:
			case CHARACTER:
			case DOUBLE:
			case FLOAT:
			case INTEGER:
			case LONG:
			case MAP:
			case OBJECT:
			case SHORT:
			case STRING:
			default:
				// Cannot happen because of checks
				throw new UnconvertableException();
		}
	}

	private static Class<?> getComponentType(ResolvedType type) {
		Class<?> fieldType = type.getType();
		if (fieldType.isArray()) {
			return fieldType.getComponentType();
		}
		throw new UnconvertableException("Incompatible types: " + fieldType.getName() + " and " + Type.LIST);
	}

	// TODO make it work
	private static Object createArrayObject(ResolvedType resolvedType, List<Value> value) {
		int length = value.size();
		Class<?> componentType = getComponentType(resolvedType);
		Object retval = Array.newInstance(componentType, length);
		for (int i = 0; i < length; i++) {
			Value element = value.get(i);
			Array.set(retval, i, valueToObject(ResolvedType.fromComponentType(componentType), element));
		}
		return retval;
	}

	@SuppressWarnings("unchecked")
	private static Object createListObject(ResolvedType resolvedType, List<Value> value) {
		List<Object> retval = (List<Object>) getInstance(resolvedType, ArrayList.class);
		ResolvedType genericType = resolvedType.getGenericTypes()[0];
		value.forEach(element -> {
			Object object = valueToObject(genericType, element);
			retval.add(object);
		});
		return retval;
	}

	@SuppressWarnings("unchecked")
	private static Object createSetObject(ResolvedType resolvedType, List<Value> value) {
		Set<Object> retval = (Set<Object>) getInstance(resolvedType, HashSet.class);
		ResolvedType genericType = resolvedType.getGenericTypes()[0];
		value.forEach(element -> {
			Object object = valueToObject(genericType, element);
			retval.add(object);
		});
		return retval;
	}

	private static Object getInstance(ResolvedType resolvedType, Class<?> clazz) {
		Class<?> type = resolvedType.getType();
		try {
			if (type.isInterface()) {
				return clazz.newInstance();
			}
			return type.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			Utility.throwUnchecked(e);
			return null;
		}
	}

	private static Object createMapObject(ConcreteType concreteType, ResolvedType type, Value value) {
		switch (concreteType) {
			case MAP:
				return createMapObject(type, value.getValue());
			case OBJECT:
				return createBeanObject(type.getType(), value);
			case ARRAY:
			case BOOLEAN:
			case BYTE:
			case CHARACTER:
			case DOUBLE:
			case FLOAT:
			case INTEGER:
			case LIST:
			case LONG:
			case SET:
			case SHORT:
			case STRING:
			default:
				// Should not happen
				throw new UnconvertableException();
		}
	}

	@SuppressWarnings("unchecked")
	private static Object createMapObject(ResolvedType type, Map<String, Value> values) {
		Map<Object, Object> retval = (Map<Object, Object>) getInstance(type, HashMap.class);
		ResolvedType keyResolvedType = type.getGenericTypes()[0];
		ResolvedType valueResolvedType = type.getGenericTypes()[1];
		values.forEach((key, value) -> {
			ConcreteType keyType = getConcreteType(keyResolvedType.getType());
			Object keyValue = createScalarObject(keyType, key);
			Object valueValue = valueToObject(valueResolvedType, value);
			retval.put(keyValue, valueValue);
		});
		return retval;
	}

	private static ConcreteType getConcreteType(Class<?> clazz) {
		if (Boolean.class.isAssignableFrom(clazz) || boolean.class.isAssignableFrom(clazz)) {
			return ConcreteType.BOOLEAN;
		}
		if (Byte.class.isAssignableFrom(clazz) || byte.class.isAssignableFrom(clazz)) {
			return ConcreteType.BYTE;
		}
		if (Integer.class.isAssignableFrom(clazz) || int.class.isAssignableFrom(clazz)) {
			return ConcreteType.INTEGER;
		}
		if (Long.class.isAssignableFrom(clazz) || long.class.isAssignableFrom(clazz)) {
			return ConcreteType.LONG;
		}
		if (Short.class.isAssignableFrom(clazz) || short.class.isAssignableFrom(clazz)) {
			return ConcreteType.SHORT;
		}
		if (Float.class.isAssignableFrom(clazz) || float.class.isAssignableFrom(clazz)) {
			return ConcreteType.FLOAT;
		}
		if (Double.class.isAssignableFrom(clazz) || double.class.isAssignableFrom(clazz)) {
			return ConcreteType.DOUBLE;
		}
		if (Character.class.isAssignableFrom(clazz) || char.class.isAssignableFrom(clazz)) {
			return ConcreteType.CHARACTER;
		}
		if (String.class.isAssignableFrom(clazz)) {
			return ConcreteType.STRING;
		}
		if (clazz.isArray()) {
			return ConcreteType.ARRAY;
		}
		if (Set.class.isAssignableFrom(clazz)) {
			return ConcreteType.SET;
		}
		if (Collection.class.isAssignableFrom(clazz)) {
			return ConcreteType.LIST;
		}
		if (Map.class.isAssignableFrom(clazz)) {
			return ConcreteType.MAP;
		}
		return ConcreteType.OBJECT;
	}

	private static boolean isScalarType(ConcreteType type) {
		switch (type) {
			case BOOLEAN:
			case BYTE:
			case INTEGER:
			case LONG:
			case SHORT:
			case FLOAT:
			case DOUBLE:
			case CHARACTER:
			case STRING:
				return true;
			case ARRAY:
			case LIST:
			case OBJECT:
			case MAP:
			case SET:
			default:
				// Cannot happen because of checks
				return false;
		}
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
