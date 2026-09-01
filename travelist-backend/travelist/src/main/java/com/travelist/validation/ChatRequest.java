package com.travelist.validation;

import com.travelist.entity.ChatMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI 聊天请求体(全量历史消息,前端每轮携带)。
 */
@Data
public class ChatRequest
{
	@NotEmpty(message = "消息列表不能为空")
	@Size(max = 50, message = "消息条数不能超过50条")
	private List<@Valid ChatMessage> messages;

	/** 可选:当前查看的景点 id,作为聊天上下文注入。 */
	private Integer spotId;
}
