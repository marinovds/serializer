package com.github.marinovds;

import java.util.HashMap;
import java.util.Map;

public class MapperFactory {

	private static final String FORBIDDEN_PREFIX = "ser_";
	private static Map<String, Marshaller> holder;

	static {
		holder = new HashMap<>();
		// TODO add default implementations
	}

	private MapperFactory( ) {
		throw new UnsupportedOperationException("Utility classes cannot be instantiated");
	}

	public static Marshaller getMapper(String format) {
		validateFormat(format);
		return getMapperByFormat(format);
	}

	private static void validateFormat(String format) {
		if (format.startsWith(FORBIDDEN_PREFIX)) {
			throw new IllegalArgumentException("'" + FORBIDDEN_PREFIX + "' prefix is forbidden.");
		}
	}

	private static Marshaller getMapperByFormat(String format) {
		Marshaller retval = holder.get(format);
		if (retval == null) {
			return getDefault(format);
		}
		return retval;
	}

	private static Marshaller getDefault(String format) {
		return holder.get(toDefaultFormatName(format));
	}

	public static boolean addFormat(String formatName, Serializer serializer) {
		validateFormat(formatName);
		return holder.put(formatName, new MarshallerImpl(formatName, serializer)) == null;
	}

	private static String toDefaultFormatName(String format) {
		if (FormatNames.XML.equals(format)) {
			return FORBIDDEN_PREFIX.concat(FormatNames.XML).intern();
		}
		if (FormatNames.JSON.equals(format)) {
			return FORBIDDEN_PREFIX.concat(FormatNames.JSON).intern();
		}
		throw new IllegalArgumentException("Unknown format: " + format);
	}

}
