package com.github.marinovds;

public interface MarshallerContext {

	Marshaller getMarshaller(String format);

	boolean addFormat(String formatName, Serializer serializer);

	boolean removeFormat(String formatName);

}