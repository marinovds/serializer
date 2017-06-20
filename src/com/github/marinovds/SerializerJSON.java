package com.github.marinovds;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.github.marinovds.Value.Type;
import com.github.marinovds.exceptions.UnserializableException;

public class SerializerJSON implements Serializer {

	private static final char SPACE = ' ';
	private static final int NOT_FOUND = -1;
	private static final String OPERATOR_ASSING = ": ";
	private static final String TRUE = "true";
	private static final String FALSE = "false";
	private static final String NEW_LINE = "\n";
	private static final char TAB = '\t';
	private static final char OPENING_BRACKET = '{';
	private static final char OPENING_ARRAY_BRACKET = '[';
	private static final String CLOSING_BRACKET = "}";
	private static final String CLOSING_ARRAY_BRACKET = "]";
	private static final String NULL = "null";
	private static final char COMMA = ',';
	private static final char QUOTE = '\"';

	@Override
	public void serialize(Value value, OutputStream stream) throws UnserializableException {
		int indentation = 0;
		try (PrintWriter writer = new PrintWriter(stream);) {
			if (value.getType() != Type.MAP) {
				throw new UnserializableException("Invalid value input.");
			}
			Map<String, Value> realValue = value.getValue();
			realValue.forEach((tagName, tagValue) -> {
				writer.print(writeValue(tagValue, indentation));
			});
			writer.flush();
		}
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
				return writeScalarValue(tagValue.getValue());
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
				return NULL;

		}
	}

	private static String writeScalarValue(String value) {
		if (isDigit(value)) {
			return value;
		}
		if (isBoolean(value)) {
			return value;
		}
		return prettify(value);
	}

	private static boolean isDigit(String value) {
		try {
			Double.valueOf(value);
			return true;
		} catch (@SuppressWarnings("unused") Exception e) {
			return false;
		}
	}

	private static boolean isBoolean(String value) {
		return (TRUE.equalsIgnoreCase(value) || FALSE.equalsIgnoreCase(value));
	}

	private static String prettify(String value) {
		return QUOTE + value + QUOTE;
	}

	private String writeListValue(List<Value> listValue, int indentation) {
		StringBuilder retval = new StringBuilder();
		retval.append(OPENING_ARRAY_BRACKET);
		listValue.forEach(value -> {
			if (value.getType() == Type.NULL) {
				retval.append(NULL);
			}
			retval.append(writeValue(value, indentation));
			retval.append(COMMA);
		});
		closeOpeningBracket(CLOSING_ARRAY_BRACKET, retval);
		return retval.toString();
	}

	private static void closeOpeningBracket(String closingBracket, StringBuilder retval) {
		int length = retval.lastIndexOf(String.valueOf(COMMA));
		if (length != NOT_FOUND) {
			retval.replace(length, length + 1, closingBracket);
			return;
		}
		retval.append(closingBracket);
	}

	private String writeMapValue(Map<String, Value> mapValue, int indentation) {
		StringBuilder retval = new StringBuilder();
		retval.append(OPENING_BRACKET);
		mapValue.forEach((elementName, value) -> {
			if (value.getType() == Type.NULL) {
				retval.append(NULL);
			}
			retval.append(NEW_LINE);
			retval.append(indent(indentation));
			retval.append(writeOpeningElement(elementName));
			retval.append(writeValue(value, indentation));
			retval.append(COMMA);
		});
		closeOpeningBracket(NEW_LINE + indent(indentation - 1) + CLOSING_BRACKET, retval);
		return retval.toString();
	}

	private static String writeOpeningElement(String elementName) {
		return prettify(elementName) + OPERATOR_ASSING;
	}

	@Override
	public Value deserialize(Class<?> clazz, InputStream stream) throws UnserializableException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream));) {
			StringBuilder buffer = getBuffer(reader);
			return readMapValue(buffer);
		} catch (IOException e) {
			throw new UnserializableException("Object could not be deserialized", e);
		}

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

	private static Value readMapValue(StringBuilder input) throws UnserializableException {
		Map<String, Value> retval = new HashMap<>();
		delete(input, String.valueOf(OPENING_BRACKET));
		do {
			removeComma(input);
			String key = extractKey(input);
			Value value = readValue(input);
			retval.put(key, value);
		} while (input.charAt(0) == COMMA);
		require(input, CLOSING_BRACKET);
		return Value.createMap(retval);
	}

	private static String extractKey(StringBuilder input) {
		int index = input.indexOf(OPERATOR_ASSING);
		String key = input.substring(0, index);
		input.delete(0, index + 1);
		return stripQuotes(key);
	}

	private static String stripQuotes(String input) {
		if (hasQuotes(input)) {
			int index = input.indexOf(QUOTE);
			return input.substring(index + 1, input.length() - 1);
		}
		return input;
	}

	private static Value readValue(StringBuilder input) throws UnserializableException {
		if (isListValue(input)) {
			return readListValue(input);
		}
		if (isMapValue(input)) {
			return readMapValue(input);
		}
		return readScalarValue(input);
	}

	private static Value readScalarValue(StringBuilder input) throws UnserializableException {
		String value = extractValue(input);
		return (value == null) ? null : Value.createScalar(value);
	}

	private static String extractValue(StringBuilder input) throws UnserializableException {
		int index = getIndex(input);
		String key = input.substring(0, index).trim();
		if (key.equals("")) {
			return null;
		}
		input.delete(0, index);
		return stripQuotes(key);
	}

	private static int getIndex(StringBuilder input) throws UnserializableException {
		int indexComma = input.indexOf(String.valueOf(COMMA));
		int indexArray = input.indexOf(CLOSING_ARRAY_BRACKET);
		int indexObject = input.indexOf(CLOSING_BRACKET);
		int retval = findFirst(indexComma, indexArray, indexObject);
		if (retval == NOT_FOUND) {
			throw new UnserializableException("Object cannot be deserialized");
		}
		return retval;
	}

	private static int findFirst(int... input) {
		int retval = Integer.MAX_VALUE;
		for (int entry : input) {
			if ((entry != NOT_FOUND) && entry < retval) {
				retval = entry;
			}
		}
		return (retval == Integer.MAX_VALUE) ? NOT_FOUND : retval;
	}

	private static Value readListValue(StringBuilder input) throws UnserializableException {
		List<Value> retval = new ArrayList<>();
		delete(input, String.valueOf(OPENING_ARRAY_BRACKET));
		do {
			removeComma(input);
			Value value = readValue(input);
			retval.add(value);
		} while (input.charAt(0) == COMMA);
		require(input, CLOSING_ARRAY_BRACKET);
		removeNulls(retval);
		return Value.createList(retval);
	}

	private static void removeNulls(List<Value> list) {
		for (Iterator<Value> iterator = list.iterator(); iterator.hasNext();) {
			Value value = iterator.next();
			if (value == null) {
				iterator.remove();
			}
		}
	}

	private static void removeComma(StringBuilder input) {
		if (input.charAt(0) == COMMA) {
			input.deleteCharAt(0);
		}
	}

	private static void delete(StringBuilder input, String string) {
		int index = input.indexOf(string);
		input.delete(0, index + string.length());
	}

	private static boolean hasQuotes(String input) {
		int index = input.indexOf(QUOTE);
		int lastIndex = input.lastIndexOf(QUOTE);
		return (index != NOT_FOUND && lastIndex != index);
	}

	private static boolean isValueType(StringBuilder input, char valueType) {
		char type = getFirstChar(input);
		return type == valueType;
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

	private static boolean isMapValue(StringBuilder input) {
		return isValueType(input, OPENING_BRACKET);
	}

	private static boolean isListValue(StringBuilder input) {
		return isValueType(input, OPENING_ARRAY_BRACKET);
	}

	private static void require(StringBuilder input, String string) throws UnserializableException {
		int index = input.indexOf(string);
		if (index == NOT_FOUND) {
			throw new UnserializableException("Cannot deserialize");
		}
		input.delete(0, index + string.length());
	}
}
