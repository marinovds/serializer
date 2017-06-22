package com.github.marinovds;

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

import com.github.marinovds.Value.Type;
import com.github.marinovds.exceptions.UnserializableException;

class SerializerXML implements Serializer {

	private static final String NEW_LINE = "\n";
	private static final char TAB = '\t';
	private static final char OPENING_BRACKET = '<';
	private static final String CLOSING_BRACKET = ">";
	private static final char TAG_CLOSER = '/';
	private static final String ELEMENT = "element";
	private static final char SPACE = ' ';
	private static final String CLOSING_TAG_BRAKET = "" + OPENING_BRACKET + TAG_CLOSER;

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
			if (value.getType() == Type.NULL) {
				retval.append(writeEmptyTag(ELEMENT));
			}
			retval.append(NEW_LINE);
			retval.append(indent(indentation));
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
			if (value.getType() == Type.NULL) {
				retval.append(writeEmptyTag(tagName));
			}
			retval.append(NEW_LINE);
			retval.append(indent(indentation));
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

	@Override
	public Value deserialize(Class<?> clazz, InputStream stream) throws UnserializableException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream));) {
			StringBuilder buffer = getBuffer(reader);
			readObjectValue(buffer, clazz);
			Entry entry = readEntry(buffer, clazz);
			return Value.createMap(Collections.singletonMap(entry.getTagName(), entry.getTagValue()));
		} catch (IOException e) {
			throw new UnserializableException("Object could not be deserialized", e);
		}
	}

	private Value readObjectValue(StringBuilder input, Class<?> clazz) throws UnserializableException {
		Map<String, Value> retval = new HashMap<>();
		String className = getOpeningTag(input);
		do {
			stripClosingBracket(input);
			String tagName = getOpeningTag(input);
			Field field;
			try {
				field = clazz.getDeclaredField(tagName);
			} catch (NoSuchFieldException | SecurityException e) {
				throw new UnserializableException("Object cannot be deserialized", e);
			}
			ResolvedType type = ResolvedType.create(field);
			// TODO
			Value value = readValue(input, null, null);
			retval.put(tagName, value);
		} while (!isClosingTag(input, className));
		delete(input, writeClosingTag(className));
		return Value.createMap(retval);
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

	private static Value readValue(StringBuilder input, String tagName, Class<?> clazz) throws UnserializableException {
		XMLType type = getXMLType(clazz);
		if (isEmptyTag(input)) {
			removeEmpty(input);
			return Value.createNull();
		}
		if (isListValue(input)) {
			Value retval = readListValue(input, tagName, clazz);

			return retval;
		}
		Value retval = readScalarValue(input);
		delete(input, writeClosingTag(tagName));
		return retval;
	}

	private static Value readScalarValue(StringBuilder input) {
		int index = input.indexOf(String.valueOf(OPENING_BRACKET));
		String retval = input.substring(0, index);
		delete(input, retval);
		return Value.createScalar(retval);
	}

	private static Value readListValue(StringBuilder input, String tagName, Class<?> clazz)
			throws UnserializableException {
		List<Entry> retval = new ArrayList<>();
		do {
			stripClosingBracket(input);
			Entry entry = readEntry(input, clazz);
			retval.add(entry);
		} while (!isClosingTag(input, tagName));
		delete(input, writeClosingTag(tagName));
		return toValue(retval);
	}

	private static Entry readEntry(StringBuilder input, Class<?> clazz) throws UnserializableException {
		String tagName = getOpeningTag(input);
		stripClosingBracket(input);
		return new Entry(tagName, readValue(input, tagName, clazz));
	}

	private static Value toValue(List<Entry> input) {
		if (hasSameKeys(input)) {
			return createListValue(input);
		}
		return createMapValue(input);
	}

	private static Value createMapValue(List<Entry> input) {
		Map<String, Value> retval = new HashMap<>();
		input.forEach(entry -> {
			String key = entry.getTagName();
			Value value = entry.getTagValue();
			retval.put(key, value);
		});
		return Value.createMap(retval);
	}

	private static Value createListValue(List<Entry> input) {
		List<Value> retval = new ArrayList<>();
		input.forEach(entry -> {
			Value value = entry.getTagValue();
			retval.add(value);
		});
		return Value.createList(retval);
	}

	private static boolean hasSameKeys(List<Entry> input) {
		for (Entry entry : input) {
			return entry.getTagName().equals(ELEMENT);
		}
		return false;
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

	private static boolean isValueType(StringBuilder input, char valueType) {
		char type = getFirstChar(input);
		return type == valueType;
	}

	private static boolean isListValue(StringBuilder input) {
		return isValueType(input, OPENING_BRACKET) && notClosingTag(input);
	}

	private static boolean notClosingTag(StringBuilder input) {
		int openingIndex = getFirstCharIndex(input);
		int closingIndex = input.indexOf(CLOSING_TAG_BRAKET);
		return openingIndex != closingIndex;
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

	private static boolean isEmptyTag(StringBuilder input) {
		return isValueType(input, TAG_CLOSER);
	}

	private static XMLType getXMLType(Class<?> clazz) {
		if (Collection.class.isAssignableFrom(clazz) || clazz.isArray()) {
			return XMLType.LIST;
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

	private static class Entry {

		private final String tagName;
		private final Value tagValue;

		public Entry(String name, Value value) {
			this.tagName = name;
			this.tagValue = value;
		}

		public String getTagName() {
			return this.tagName;
		}

		public Value getTagValue() {
			return this.tagValue;
		}

		@Override
		public String toString() {
			return this.tagName + ": " + this.tagValue;
		}
	}

	private static enum XMLType {
		SCALAR, LIST, MAP, OBJECT;
	}
}
