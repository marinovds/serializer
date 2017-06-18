package com.github.marinovds;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import com.github.marinovds.Value.Type;
import com.github.marinovds.exceptions.UnserializableException;

public class SerializerJSON implements Serializer {

	private static final String TRUE = "true";
	private static final String FALSE = "false";
	private static final String NEW_LINE = "\n";
	private static final String TAB = "\t";
	private static final String OPENING_BRACKET = "{";
	private static final String OPENING_ARRAY_BRACKET = "[";
	private static final String CLOSING_BRACKET = "}";
	private static final String CLOSING_ARRAY_BRACKET = "]";
	private static final String NULL = "null";
	private static final String COMMA = ",";
	private static final String QUOTE = "\"";

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
				break;

		}
		return null;
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
		int length = retval.lastIndexOf(COMMA);
		if (length != -1) {
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
		return prettify(elementName) + ": ";
	}

	@Override
	public Value deserialize(Class<?> clazz, InputStream stream) {
		// TODO Auto-generated method stub
		return null;
	}

}
