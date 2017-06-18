package com.github.marinovds;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import com.github.marinovds.Value.Type;
import com.github.marinovds.exceptions.UnserializableException;

class SerializerXML implements Serializer {

	private static final String NEW_LINE = "\n";
	private static final String TAB = "\t";
	private static final String OPENING_BRACKET = "<";
	private static final String CLOSING_BRACKET = ">";
	private static final String TAG_CLOSER = "/";
	private static final String ELEMENT = "element";

	@Override
	public void serialize(Value value, OutputStream stream) throws UnserializableException {
		int indentation = 0;
		PrintWriter writer = new PrintWriter(stream);
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
		writer.close();
	}

	private static String writeClosingTag(String tagName) {
		return OPENING_BRACKET + TAG_CLOSER + tagName + CLOSING_BRACKET;
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

	private String writeEmptyTag(String tagName) {
		return OPENING_BRACKET + tagName + TAG_CLOSER + CLOSING_BRACKET;
	}

	@Override
	public Value deserialize(Class<?> clazz, InputStream stream) {
		// TODO Auto-generated method stub
		return null;
	}

}
