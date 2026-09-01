package com.travelist.service;

import com.travelist.entity.ChatMessage;
import com.travelist.entity.Spot;
import com.travelist.Util.LLMUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * AI 聊天服务:拼接系统提示词(含景点上下文)并转发 LLM 流式输出。
 */
@Service
public class ChatService
{
	private static final String SYSTEM_PROMPT = """
			你是 Travelist 的 AI 旅行助手。
			用户会向你咨询旅游、行程规划、美食与交通等问题。
			回答要求:
			1. 简洁、实用、有条理,优先给出可执行的建议。
			2. 使用中文回答,可以使用 Markdown 排版(列表、加粗、小标题)。
			3. 若用户提供了当前正在查看的景点上下文,回答应结合该景点展开;用户未主动询问时无需长篇展开。
			4. 不确定的信息请明确说明,不要编造。
			""";

	@Resource
	private LLMUtil llmUtil;

	@Resource
	private SpotService spotService;

	/**
	 * 解析景点上下文,组装最终系统提示词。
	 * spotId 非法时抛出 IllegalArgumentException(由全局异常处理器转为 400 JSON)。
	 */
	public String resolveSystemPrompt(Integer spotId)
	{
		String base = SYSTEM_PROMPT;
		if (spotId == null)
		{
			return base;
		}
		Spot spot = spotService.findById(spotId)
		                       .orElseThrow(() -> new IllegalArgumentException("景点不存在: " + spotId));
		return base + "\n当前用户正在查看景点「" + spot.getName() + "」: " + spot.getDesc() + "。" + spot.getDetail();
	}

	/**
	 * 流式调用 LLM 并把增量文本交给回调。
	 */
	public void stream(String systemPrompt, List<ChatMessage> messages, Consumer<String> onDelta, AtomicBoolean cancelled)
	{
		llmUtil.streamMessages(systemPrompt, messages, onDelta, cancelled);
	}
}
