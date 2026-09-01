package com.travelist.Util;

/**
 * LLM 调用过程中的业务异常。
 */
public class LLMException extends RuntimeException
{
	public LLMException(String message)
	{
		super(message);
	}

	public LLMException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
