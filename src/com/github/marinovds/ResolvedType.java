package com.github.marinovds;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

final class ResolvedType {

	static final ResolvedType[] EMPTY_TYPES = new ResolvedType[0];

	private final Class<?> clazz;
	private final ResolvedType[] genericTypes;

	public ResolvedType(Class<?> type) {
		this(type, EMPTY_TYPES);
	}

	public ResolvedType(Class<?> type, ResolvedType[] generics) {
		this.clazz = type;
		this.genericTypes = generics;
	}

	public Class<?> getType() {
		return this.clazz;
	}

	public ResolvedType[] getGenericTypes() {
		return this.genericTypes;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(this.clazz.getName());
		appendBracket(builder, '<');
		int length = this.genericTypes.length;
		for (int i = 0; i < length; i++) {
			String type = this.genericTypes[i].toString();
			builder.append(type);
			if (i != length - 1) {
				builder.append(", ");
			}
		}
		appendBracket(builder, '>');
		return builder.toString();
	}

	private void appendBracket(StringBuilder builder, char bracket) {
		if (this.genericTypes.length != 0) {
			builder.append(bracket);
		}
	}

	public static ResolvedType create(Field field) {
		Class<?> clazz = field.getType();
		if (clazz.isArray()) {
			return new ResolvedType(clazz);
		}
		Type type = field.getGenericType();
		return TypeParser.parse(type.getTypeName());
	}

	private static class TypeParser {

		private static final char GENERIC_OPENING = '<';
		private static final char GENERIC_CLOSING = '>';
		private static final int NOT_FOUND = -1;

		public static ResolvedType parse(String type) {

			int opening = type.indexOf(GENERIC_OPENING);
			if (opening == NOT_FOUND) {
				Class<?> realType = getType(type);
				return new ResolvedType(realType);
			}
			int closing = type.lastIndexOf(GENERIC_CLOSING);
			String className = type.substring(0, opening);
			Class<?> realType = getType(className);
			String genericsString = type.substring(opening + 1, closing);
			ResolvedType[] generics = getGenerics(genericsString);
			return new ResolvedType(realType, generics);
		}

		private static Class<?> getType(String className) {
			try {
				return Class.forName(className);
			} catch (ClassNotFoundException e) {
				Utility.throwUnchecked(e);
				// Cannot happen
				return null;
			}
		}

		private static ResolvedType[] getGenerics(String genericsString) {

			String[] generics = genericsString.split(", ");
			int length = generics.length;
			ResolvedType[] retval = (length == 0) ? EMPTY_TYPES : new ResolvedType[length];
			for (int i = 0; i < length; i++) {
				String type = generics[i];
				retval[i] = parse(type);
			}
			return retval;
		}

	}
}