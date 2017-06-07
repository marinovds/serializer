package com.github.marinovds;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class MarshallerFactory {

	private static final String FORBIDDEN_PREFIX = "ser_";
	private static Map<String, Marshaller> holder;

	static {
		holder = new HashMap<>();
		// TODO add default implementations
	}

	private MarshallerFactory( ) {
		throw new UnsupportedOperationException("Utility classes cannot be instantiated");
	}

	public static Marshaller getMapper(String format) {
		validateFormat(format);
		return getMapperByFormat(format);
	}

	private static void validateFormat(String format) {
		if (format == null) {
			throw new IllegalArgumentException("Format cannot be null");
		}
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

	public static boolean removeFormat(String formatName) {
		// TODO why would you like to do that?!
		validateFormat(formatName);
		return holder.remove(formatName) != null;
	}

	// TODO optimize. If more formats are added this method will expand
	// drastically
	private static String toDefaultFormatName(String format) {
		if (FormatNames.XML.equals(format)) {
			return FORBIDDEN_PREFIX + FormatNames.XML;
		}
		if (FormatNames.JSON.equals(format)) {
			return FORBIDDEN_PREFIX + FormatNames.JSON;
		}
		throw new IllegalArgumentException("Unknown format: " + format);
	}

	private static class MarshallerImpl implements Marshaller {

		private String format;
		private Serializer serializer;

		public MarshallerImpl(String formatName, Serializer serializer) {
			this.format = formatName;
			this.serializer = serializer;
		}

		@Override
		public String getFormat() {
			return this.format;
		}

		@Override
		public void serialize(Object object, OutputStream stream) {
			Value mappedValue = Mapper.toValue(object);
			this.serializer.serialize(mappedValue, stream);
		}

		@Override
		public <T> T deserialize(Class<T> clazz, InputStream stream) {
			Value value = this.serializer.deserialize(clazz, stream);
			return Mapper.toObject(clazz, value);
		}

	}
}
