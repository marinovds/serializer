package com.github.marinovds;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.github.marinovds.annotations.Entry;
import com.github.marinovds.annotations.Ignore;
import com.github.marinovds.annotations.Root;

class Mapper {

	private Mapper( ) {
		throw new UnsupportedOperationException("Utility classes cannot be instantiated");
	}

	public static Value toValue(Object object) {
		Class<?> clazz = object.getClass();
		String rootName = getRootName(clazz);
		Value rootValue = getRootValue(clazz);
		return Value.createMap(Collections.singletonMap(rootName, rootValue));
	}

	private static String getRootName(Class<?> clazz) {
		Root root = clazz.getAnnotation(Root.class);
		if (root != null) {
			return root.name();
		}
		return clazz.getSimpleName();
	}

	private static Value getRootValue(Class<?> clazz) {
		Map<String, Value> retval = new HashMap<>();
		Set<Field> fields = getSerializableFields(clazz.getDeclaredFields());
		for (Field field : fields) {
			String entryName = getEntryName(field);
			Value entryValue = getEntryValue(field);
			retval.put(entryName, entryValue);
		}
		return Value.createMap(retval);
	}

	private static Set<Field> getSerializableFields(Field[] fields) {
		Set<Field> retval = new HashSet<>();
		for (Field field : fields) {
			if (shouldSerialize(field)) {
				retval.add(field);
			}
		}
		return retval;
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

	private static Value getEntryValue(Field field) {
		Class<?> type = field.getType();
		// TODO how to extract the value?
		return null;
	}

	public static <T> T toObject(Class<T> clazz, Value value) {
		// TODO Auto-generated method stub
		return null;
	}

}
