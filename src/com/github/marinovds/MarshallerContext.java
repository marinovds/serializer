package com.github.marinovds;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import com.github.marinovds.exceptions.UnserializableException;

public final class MarshallerContext {

	private static final Map<String, Marshaller> defaultMarshallers;
	private Map<String, Marshaller> holder;

	static {
		defaultMarshallers = new HashMap<>();
		defaultMarshallers.put(FormatNames.XML, new MarshallerImpl(FormatNames.XML, new SerializerXML()));
		defaultMarshallers.put(FormatNames.JSON, new MarshallerImpl(FormatNames.JSON, new SerializerJSON()));
	}

	MarshallerContext() {
		this.holder = new HashMap<>();
	}

	public Marshaller getMarshaller(String format) {
		Utility.validateInput(format, "Format cannot be null");
		return getMapperByFormat(format);
	}

	private Marshaller getMapperByFormat(String format) {
		Marshaller retval = this.holder.get(format);
		if (retval == null) {
			return getDefault(format);
		}
		return retval;
	}

	private static Marshaller getDefault(String format) {
		Marshaller retval = defaultMarshallers.get(format);
		if (retval == null) {
			throw new IllegalArgumentException("Unknown format was passed");
		}
		return retval;
	}

	public boolean addFormat(String formatName, Serializer serializer) {
		Utility.validateInput(formatName, "Format cannot be null");
		return this.holder.put(formatName, new MarshallerImpl(formatName, serializer)) == null;
	}

	public boolean removeFormat(String formatName) {
		// TODO why would you like to do that?!
		Utility.validateInput(formatName, "Format cannot be null");
		return this.holder.remove(formatName) != null;
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
		public void serialize(Object object, OutputStream stream) throws UnserializableException {
			Value mappedValue = Mapper.toValue(object);
			this.serializer.serialize(mappedValue, stream);
		}

		@Override
		public <T> T deserialize(Class<T> clazz, InputStream stream) throws UnserializableException {
			Value value = this.serializer.deserialize(clazz, stream);
			return Mapper.toObject(clazz, value);
		}

	}
}
