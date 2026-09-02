package com.travelist.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

/**
 * List&lt;String&gt; ↔ JSON 文本,用于景点亮点等列表字段落库。
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String>
{
	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String convertToDatabaseColumn(List<String> attribute)
	{
		if (attribute == null)
		{
			return "[]";
		}
		try
		{
			return objectMapper.writeValueAsString(attribute);
		}
		catch (JacksonException e)
		{
			return "[]";
		}
	}

	@Override
	public List<String> convertToEntityAttribute(String dbData)
	{
		if (dbData == null || dbData.isBlank())
		{
			return Collections.emptyList();
		}
		try
		{
			return objectMapper.readValue(dbData, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
		}
		catch (JacksonException e)
		{
			return Collections.emptyList();
		}
	}
}
