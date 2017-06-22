package com.github.marinovds.serializer;

public interface MarshallerContext {

	Marshaller getMarshaller(String format);

	boolean addFormat(String formatName, Serializer serializer);

	boolean removeFormat(String formatName);

}