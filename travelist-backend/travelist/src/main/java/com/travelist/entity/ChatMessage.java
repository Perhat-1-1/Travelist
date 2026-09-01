package com.travelist.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 聊天消息(单条)。
 * role 仅允许 user / assistant,与 OpenAI 兼容协议一致。
 */
@Data
public class ChatMessage
{
	@NotBlank(message = "消息角色不能为空")
	@Pattern(regexp = "user|assistant", message = "消息角色只能是 user 或 assistant")
	private String role;

	@NotBlank(message = "消息内容不能为空")
	@Size(max = 2000, message = "单条消息不能超过2000字")
	private String content;
}
