package com.github.marinovds.serializer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.marinovds.serializer.Value.Type;
import com.github.marinovds.serializer.annotations.Entry;
import com.github.marinovds.serializer.exceptions.UnserializableException;

class SerializerXML implements Serializer {

	private static final String NEW_LINE = "\n";
	private static final char TAB = '\t';
	private static final char OPENING_BRACKET = '<';
	private static final String CLOSING_BRACKET = ">";
	private static final char TAG_CLOSER = '/';
	private static final String ELEMENT = "element";
	private static final char SPACE = ' ';
	private static final String CLOSING_TAG_BRAKET = "" + OPENING_BRACKET + TAG_CLOSER;
	private static final int NOT_FOUND = 0;

	@Override
	public void serialize(Value value, OutputStream stream) throws UnserializableException {
		int indentation = 0;
		try (PrintWriter writer = new PrintWriter(stream)) {
			if (value.getType() != Type.MAP) {
				throw new UnserializableException("Invalid value input.");
			}
			Map<String, Value> realValue = value.getValue();
			realValue.forEach((tagName, tagValue) -> {
				writer.print(writeOpeningTag(tagName));
				writer.print(writeValue(tagValue, indentation));
				writer.print(writeClosingTag(tagName));
				writer.flush();
			});
		}
	}

	private static String writeClosingTag(String tagName) {
		return CLOSING_TAG_BRAKET + tagName + CLOSING_BRACKET;
	}

	private static String writeOpeningTag(String tagName) {
		return OPENING_BRACKET + tagName + CLOSING_BRACKET;
	}

	private static String indent(int indentation) {
		StringBuilder retval = new StringBuilder();
		for (int i = 0; i < indentation; i++) {
			retval.append(TAB);
		}
		return retval.toString();
	}

	private String writeValue(Value tagValue, int indentation) {
		Type type = tagValue.getType();
		String retval;
		switch (type) {
			case SCALAR:
				return tagValue.getValue();
			case LIST:
				indentation++;
				retval = writeListValue(tagValue.getValue(), indentation);
				indentation--;
				return retval;
			case MAP:
				indentation++;
				retval = writeMapValue(tagValue.getValue(), indentation);
				indentation--;
				return retval;
			case NULL:
			default:
				break;

		}
		return null;
	}

	private String writeListValue(List<Value> listValue, int indentation) {
		StringBuilder retval = new StringBuilder();
		listValue.forEach(value -> {
			retval.append(NEW_LINE);
			retval.append(indent(indentation));
			if (value.getType() == Type.NULL) {
				retval.append(writeEmptyTag(ELEMENT));
				return;
			}
			retval.append(writeOpeningTag(ELEMENT));
			retval.append(writeValue(value, indentation));
			if (value.getType() != Type.SCALAR) {
				retval.append(indent(indentation));
			}
			retval.append(writeClosingTag(ELEMENT));
		});
		retval.append(NEW_LINE);
		return retval.toString();
	}

	private String writeMapValue(Map<String, Value> mapValue, int indentation) {
		StringBuilder retval = new StringBuilder();
		mapValue.forEach((tagName, value) -> {
			retval.append(NEW_LINE);
			retval.append(indent(indentation));
			if (value.getType() == Type.NULL) {
				retval.append(writeEmptyTag(tagName));
				return;
			}
			retval.append(writeOpeningTag(tagName));
			retval.append(writeValue(value, indentation));
			if (value.getType() != Type.SCALAR) {
				retval.append(indent(indentation));
			}
			retval.append(writeClosingTag(tagName));
		});
		retval.append(NEW_LINE);
		return retval.toString();
	}

	private static String writeEmptyTag(String tagName) {
		return OPENING_BRACKET + tagName + TAG_CLOSER + CLOSING_BRACKET;
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////

	@Override
	public Value deserialize(Class<?> clazz, InputStream stream) throws UnserializableException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream));) {
			StringBuilder buffer = getBuffer(reader);
			String className = getOpeningTag(buffer);
			return readObjectValue(buffer, className, clazz);
		} catch (IOException e) {
			throw new UnserializableException("Object could not be deserialized", e);
		}
	}

	private static Value readObjectValue(StringBuilder input, String className, Class<?> clazz)
			throws UnserializableException {
		Map<String, Value> retval = new HashMap<>();
		do {
			String tagName = getOpeningTag(input);
			stripClosingBracket(input);
			if (isEmptyTag(tagName)) {
				String tag = tagName.substring(0, tagName.length() - 1);
				retval.put(tag, Value.createNull());
			} else {
				ResolvedType type = getResolvedType(tagName, clazz);
				Value value = readValue(input, tagName, type);
				retval.put(tagName, value);
			}
		} while (!isClosingTag(input, className));
		delete(input, writeClosingTag(className));
		return Value.createMap(retval);
	}

	private static ResolvedType getResolvedType(String tagName, Class<?> clazz) throws UnserializableException {
		try {
			Field field = getField(clazz, tagName);
			return ResolvedType.create(field);
		} catch (SecurityException e) {
			throw new UnserializableException("Object cannot be deserialized", e);
		}
	}

	private static Field getField(Class<?> clazz, String tagName) throws UnserializableException {
		Field[] fields = clazz.getDeclaredFields();
		for (Field field : fields) {
			String entryName = getEntryName(field);
			if (tagName.equals(entryName)) {
				field.setAccessible(true);
				return field;
			}
		}
		throw new UnserializableException("Field " + tagName + " could not be found");
	}

	private static String getEntryName(Field field) {
		Entry entry = field.getDeclaredAnnotation(Entry.class);
		if (entry != null) {
			return entry.value();
		}
		return field.getName();
	}

	private static StringBuilder getBuffer(BufferedReader reader) throws IOException {
		StringBuilder retval = new StringBuilder();
		String input = "";
		while (input != null) {
			retval.append(input);
			input = reader.readLine();
		}
		return retval;
	}

	private static Value readValue(StringBuilder input, String tagName, ResolvedType type)
			throws UnserializableException {
		XMLType xmlType = getXMLType(type.getType());
		if (isEmptyTag(tagName)) {
			removeEmpty(input);
			return Value.createNull();
		}
		switch (xmlType) {
			case ARRAY:
				ResolvedType componentType = ResolvedType.fromComponentType(type.getType().getComponentType());
				return readListValue(input, tagName, componentType);
			case LIST:
				return readListValue(input, tagName, type.getGenericTypes()[0]);
			case MAP:
				return readMapValue(input, tagName, type.getGenericTypes()[1]);
			case OBJECT:
				return readObjectValue(input, tagName, type.getType());
			case SCALAR:
				Value retval = readScalarValue(input);
				delete(input, writeClosingTag(tagName));
				return retval;
			default:
				return Value.createNull();
		}
	}

	private static Value readMapValue(StringBuilder input, String tagName, ResolvedType resolvedType)
			throws UnserializableException {
		Map<String, Value> retval = new HashMap<>();
		do {
			String keyName = getOpeningTag(input);
			stripClosingBracket(input);
			if (isClosingTag(keyName, tagName)) {
				return Value.createMap(Collections.emptyMap());
			} else if (isEmptyTag(keyName)) {
				String tag = keyName.substring(0, keyName.length() - 1);
				retval.put(tag, Value.createNull());
			} else {
				Value value = readValue(input, keyName, resolvedType);
				retval.put(keyName, value);
			}
		} while (!isClosingTag(input, tagName));
		delete(input, writeClosingTag(tagName));
		return Value.createMap(retval);
	}

	private static Value readScalarValue(StringBuilder input) {
		int index = input.indexOf(String.valueOf(OPENING_BRACKET));
		String retval = input.substring(0, index);
		delete(input, retval);
		return Value.createScalar(retval);
	}

	private static Value readListValue(StringBuilder input, String tagName, ResolvedType resolvedType)
			throws UnserializableException {
		List<Value> retval = new ArrayList<>();
		do {
			String elementName = getOpeningTag(input);
			stripClosingBracket(input);
			if (isClosingTag(elementName, tagName)) {
				return Value.createList(Collections.emptyList());
			} else if (isEmptyTag(elementName)) {
				retval.add(Value.createNull());
			} else {
				Value value = readValue(input, elementName, resolvedType);
				retval.add(value);
			}
		} while (!isClosingTag(input, tagName));
		delete(input, writeClosingTag(tagName));
		return Value.createList(retval);
	}

	private static boolean isClosingTag(String elementName, String tagName) {
		return elementName.equals(TAG_CLOSER + tagName);
	}

	private static void removeEmpty(StringBuilder input) {
		String temp = TAG_CLOSER + CLOSING_BRACKET;
		delete(input, temp);
		int index = getFirstCharIndex(input);
		input.delete(0, index);
	}

	private static void stripClosingBracket(StringBuilder input) {
		if (hasClosingBracket(input)) {
			int index = input.indexOf(CLOSING_BRACKET);
			input.delete(0, index + 1);
		}
	}

	private static boolean hasClosingBracket(StringBuilder input) {
		char character = getFirstChar(input);
		return CLOSING_BRACKET.equals(String.valueOf(character));
	}

	private static void delete(StringBuilder input, String string) {
		int index = input.indexOf(string);
		input.delete(0, index + string.length());
	}

	private static char getFirstChar(StringBuilder input) {
		int length = input.length();
		for (int i = 0; i < length; i++) {
			char retval = input.charAt(i);
			if (retval != SPACE && retval != TAB) {
				return retval;
			}
		}
		return SPACE;
	}

	private static String getOpeningTag(StringBuilder input) {
		int openingIndex = input.indexOf(String.valueOf(OPENING_BRACKET));
		int closingIndex = input.indexOf(CLOSING_BRACKET, openingIndex);
		String retval = input.substring(openingIndex + 1, closingIndex);
		delete(input, retval);
		return retval;
	}

	private static boolean isClosingTag(StringBuilder input, String tagName) {
		String endTag = writeClosingTag(tagName);
		int firstCharIndex = getFirstCharIndex(input);
		input.delete(0, firstCharIndex);
		int index = input.indexOf(endTag);
		return index == 0;
	}

	private static int getFirstCharIndex(StringBuilder input) {
		char character = getFirstChar(input);
		return input.indexOf(String.valueOf(character));
	}

	private static boolean isEmptyTag(String input) {
		return input.endsWith(String.valueOf(TAG_CLOSER));
	}

	private static XMLType getXMLType(Class<?> clazz) {
		if (Collection.class.isAssignableFrom(clazz)) {
			return XMLType.LIST;
		}
		if (clazz.isArray()) {
			return XMLType.ARRAY;
		}
		if (Map.class.isAssignableFrom(clazz)) {
			return XMLType.MAP;
		}
		if (isScalarType(clazz)) {
			return XMLType.SCALAR;
		}
		return XMLType.OBJECT;
	}

	private static boolean isScalarType(Class<?> clazz) {
		if (Boolean.class.isAssignableFrom(clazz) || boolean.class.isAssignableFrom(clazz)) {
			return true;
		}
		if (Byte.class.isAssignableFrom(clazz) || byte.class.isAssignableFrom(clazz)) {
			return true;
		}
		if (Integer.class.isAssignableFrom(clazz) || int.class.isAssignableFrom(clazz)) {
			return true;
		}
		if (Long.class.isAssignableFrom(clazz) || long.class.isAssignableFrom(clazz)) {
			return true;
		}
		if (Short.class.isAssignableFrom(clazz) || short.class.isAssignableFrom(clazz)) {
			return true;
		}
		if (Float.class.isAssignableFrom(clazz) || float.class.isAssignableFrom(clazz)) {
			return true;
		}
		if (Double.class.isAssignableFrom(clazz) || double.class.isAssignableFrom(clazz)) {
			return true;
		}
		if (Character.class.isAssignableFrom(clazz) || char.class.isAssignableFrom(clazz)) {
			return true;
		}
		if (String.class.isAssignableFrom(clazz)) {
			return true;
		}
		return false;
	}

	private static enum XMLType {
		SCALAR, ARRAY, LIST, MAP, OBJECT;
	}
}
