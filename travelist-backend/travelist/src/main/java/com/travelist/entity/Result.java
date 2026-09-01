package com.travelist.entity;

import lombok.Data;

@Data
public class Result<T>
{
	private Boolean success;
	private Integer code;
	private String message;
	private T data;
	
		public static <T> Result<T> success()
	{
		Result<T> result = new Result<T>();
		result.setSuccess(true);
		result.setCode(200);
		result.setMessage("success");
		return result;
	}
	
	public static <T> Result<T> success(T data)
	{
		Result<T> result = success();
		result.setData(data);
		return result;
	}
	
	public static <T> Result<T> success(String message)
	{
		Result<T> result = success();
		result.setMessage(message);
		return result;
	}
	
	public static <T> Result<T> success(String message, T data)
	{
		Result<T> result = success(data);
		result.setMessage(message);
		return result;
	}
	
	public static <T> Result<T> fail()
	{
		Result<T> result = new Result<T>();
		result.setSuccess(false);
		result.setCode(500);
		result.setMessage("fail");
		return result;
	}
	
	public static <T> Result<T> fail(String errorMessage)
	{
		Result<T> result = fail();
		result.setMessage(errorMessage);
		return result;
	}
	
	public static <T> Result<T> fail(String errorMessage, T data)
	{
		Result<T> result = fail(errorMessage);
		result.setData(data);
		return result;
	}
	
	public static <T> Result<T> fail(Integer code, String errorMessage)
	{
		Result<T> result = fail(errorMessage);
		result.setCode(code);
		return result;
	}
}
