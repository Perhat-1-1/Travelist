package com.travelist.controller;

import com.travelist.Util.LLMException;
import com.travelist.entity.ChatMessage;
import com.travelist.entity.ChatRecord;
import com.travelist.entity.ChatSession;
import com.travelist.entity.Result;
import com.travelist.service.ChatService;
import com.travelist.validation.ChatRequest;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 聊天接口(SSE 流式 + 会话/消息持久化)。
 * 上游验证失败时返回普通 JSON Result(在 SSE 开始之前);流已开始后的运行期错误
 * 以 SSE 帧 data: {"error":"..."} 下发。
 */
@RestController
@RequestMapping("/api/ai")
public class ChatController
{
	private static final long STREAM_TIMEOUT_MS = 120_000L;

	@Resource
	private ChatService chatService;

	@Resource
	@Qualifier("chatStreamExecutor")
	private ThreadPoolTaskExecutor chatStreamExecutor;

	/** 新建会话。 */
	@PostMapping("/session")
	public Result<ChatSession> createSession()
	{
		return Result.success(chatService.createSession());
	}

	/** 最近 20 个会话(按更新时间倒序)。 */
	@GetMapping("/session/list")
	public Result<List<ChatSession>> listSessions()
	{
		return Result.success(chatService.listSessions());
	}

	/** 某会话最近 N 条消息(默认 10,升序返回)。 */
	@GetMapping("/history")
	public Result<List<ChatRecord>> history(@RequestParam Long sessionId,
	                                        @RequestParam(defaultValue = "10") int limit)
	{
		return Result.success(chatService.history(sessionId, limit));
	}

	/** 删除会话(同步删除其消息记录)。 */
	@DeleteMapping("/session/{id}")
	public Result<Void> deleteSession(@PathVariable Long id)
	{
		chatService.deleteSession(id);
		return Result.success();
	}

	@PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter chat(@Valid @RequestBody ChatRequest request)
	{
		// 同步解析上下文:spotId 非法等在流式开始前以普通 JSON 报错
		String systemPrompt = chatService.resolveSystemPrompt(request.getSpotId());

		// 会话:缺省自动创建;显式传入但不存在时抛 400
		Long sessionId = resolveSessionId(request);

		SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
		AtomicBoolean cancelled = new AtomicBoolean(false);
		emitter.onCompletion(() -> cancelled.set(true));
		emitter.onTimeout(() -> cancelled.set(true));
		emitter.onError(e -> cancelled.set(true));

		// 请求体最后一条用户消息落库(流开始前;LLM 失败也保留用户消息)
		ChatMessage lastUser = request.getMessages().get(request.getMessages().size() - 1);
		chatService.saveUserMessage(sessionId, lastUser.getContent(), request.getSpotId());

		chatStreamExecutor.execute(() -> {
			try
			{
				StringBuilder answer = new StringBuilder();
				chatService.stream(systemPrompt, request.getMessages(),
						delta -> {
							answer.append(delta);
							try
							{
								sendData(emitter, Map.of("delta", delta));
							}
							catch (IOException e)
							{
								cancelled.set(true); // 客户端断开,停止上游读取
							}
						}, cancelled);
				sendData(emitter, Map.of("done", true));
				emitter.complete();
				// 流成功结束:助手回复落库(裁剪由 service 内部处理)
				chatService.saveAssistantMessage(sessionId, answer.toString(), request.getSpotId());
			}
			catch (Exception e)
			{
				cancelled.set(true);
				String message = e instanceof LLMException ? e.getMessage() : "服务异常: " + e.getMessage();
				try
				{
					sendData(emitter, Map.of("error", message));
				}
				catch (IOException ignored)
				{
					// 客户端已断开,忽略
				}
				emitter.complete();
			}
		});
		return emitter;
	}

	/** 返回有效的会话 id:缺省自动创建;显式传入时校验存在。 */
	private Long resolveSessionId(ChatRequest request)
	{
		if (request.getSessionId() == null)
		{
			return chatService.createSession().getId();
		}
		return chatService.requireSession(request.getSessionId()).getId();
	}

	private void sendData(SseEmitter emitter, Map<String, Object> payload) throws IOException
	{
		emitter.send(SseEmitter.event().data(toJson(payload)));
	}

	private String toJson(Map<String, Object> payload)
	{
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;
		for (Map.Entry<String, Object> entry : payload.entrySet())
		{
			if (!first)
			{
				sb.append(',');
			}
			first = false;
			sb.append('"').append(entry.getKey()).append("\":");
			Object value = entry.getValue();
			if (value instanceof CharSequence)
			{
				sb.append('"').append(escape((CharSequence) value)).append('"');
			}
			else
			{
				sb.append(value);
			}
		}
		return sb.append('}').toString();
	}

	private String escape(CharSequence value)
	{
		StringBuilder sb = new StringBuilder(value.length() + 16);
		for (int i = 0; i < value.length(); i++)
		{
			char c = value.charAt(i);
			switch (c)
			{
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default ->
				{
					if (c < 0x20)
					{
						sb.append(String.format("\\u%04x", (int) c));
					}
					else
					{
						sb.append(c);
					}
				}
			}
		}
		return sb.toString();
	}
}
