package com.travelist.common;

import com.travelist.Util.LLMException;
import com.travelist.entity.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler
{
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e)
	{
		String message = e.getBindingResult()
		                  .getFieldErrors()
		                  .stream()
		                  .map(FieldError::getDefaultMessage)
		                  .collect(Collectors.joining(", "));
		return Result.fail(400, message);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public Result<Void> handleIllegalArgumentException(IllegalArgumentException e)
	{
		return Result.fail(400, e.getMessage());
	}

	@ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
	public Result<Void> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException e)
	{
		log.warn("请求体解析失败", e);
		return Result.fail(400, "请求体格式错误");
	}

	@ExceptionHandler(LLMException.class)
	public Result<Void> handleLLMException(LLMException e)
	{
		log.error("LLM 调用失败", e);
		return Result.fail(500, e.getMessage());
	}

	@ExceptionHandler(IllegalStateException.class)
	public Result<Void> handleIllegalStateException(IllegalStateException e)
	{
		log.error("服务状态异常", e);
		return Result.fail(500, e.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public Result<Void> handleException(Exception e)
	{
		log.error("服务器内部错误", e);
		return Result.fail(500, "服务器内部错误");
	}
}
