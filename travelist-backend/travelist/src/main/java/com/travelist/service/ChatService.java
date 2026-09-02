package com.travelist.service;

import com.travelist.entity.ChatMessage;
import com.travelist.entity.ChatRecord;
import com.travelist.entity.ChatRecordRepository;
import com.travelist.entity.ChatSession;
import com.travelist.entity.Spot;
import com.travelist.Util.LLMUtil;
import com.travelist.repository.ChatSessionRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * AI 聊天服务:会话管理 + 消息持久化(每会话保留最近 10 条)+ LLM 流式输出。
 */
@Service
public class ChatService
{
	/** 每个会话保留的消息条数上限 */
	public static final int   MAX_MESSAGES_PER_SESSION = 10;
	/** 会话总量上限(超出删除最久未使用的) */
	public static final int   MAX_SESSIONS             = 30;
	private static final String DEFAULT_SESSION_TITLE  = "新会话";

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

	@Resource
	private ChatSessionRepository chatSessionRepository;

	@Resource
	private ChatRecordRepository chatRecordRepository;

	/**
	 * 创建新会话;会话数超过上限时清退最久未使用的会话(连同其消息)。
	 */
	@Transactional
	public ChatSession createSession()
	{
		while (chatSessionRepository.count() >= MAX_SESSIONS)
		{
			List<ChatSession> oldest = chatSessionRepository.findAllByOrderByUpdatedAtAsc();
			if (oldest.isEmpty())
			{
				break;
			}
			ChatSession victim = oldest.get(0);
			chatRecordRepository.deleteBySessionId(victim.getId());
			chatSessionRepository.delete(victim);
		}
		ChatSession session = new ChatSession();
		session.setTitle(DEFAULT_SESSION_TITLE);
		LocalDateTime now = LocalDateTime.now();
		session.setCreatedAt(now);
		session.setUpdatedAt(now);
		return chatSessionRepository.save(session);
	}

	/** 最近 20 个会话(按更新时间倒序)。 */
	@Transactional(readOnly = true)
	public List<ChatSession> listSessions()
	{
		return chatSessionRepository.findTop20ByOrderByUpdatedAtDesc();
	}

	/**
	 * 删除会话(连同其全部消息记录)。会话不存在时静默成功(幂等)。
	 */
	@Transactional
	public void deleteSession(Long sessionId)
	{
		chatSessionRepository.findById(sessionId).ifPresent(session -> {
			chatRecordRepository.deleteBySessionId(sessionId);
			chatSessionRepository.delete(session);
		});
	}

	/**
	 * 校验会话存在。
	 */
	@Transactional(readOnly = true)
	public ChatSession requireSession(Long sessionId)
	{
		return chatSessionRepository.findById(sessionId)
		                            .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
	}

	/** 读取某会话最近 N 条消息(按时间升序返回)。 */
	@Transactional(readOnly = true)
	public List<ChatRecord> history(Long sessionId, int limit)
	{
		requireSession(sessionId);
		int capped = Math.min(limit, MAX_MESSAGES_PER_SESSION);
		List<ChatRecord> records = chatRecordRepository.findTop10BySessionIdOrderByIdDesc(sessionId);
		if (records.size() > capped)
		{
			records = records.subList(0, capped);
		}
		List<ChatRecord> result = new java.util.ArrayList<>(records);
		Collections.reverse(result);
		return result;
	}

	/** 落库用户消息:首条消息时生成标题,并更新会话时间。 */
	@Transactional
	public ChatRecord saveUserMessage(Long sessionId, String content, Integer spotId)
	{
		ChatSession session = requireSession(sessionId);
		LocalDateTime now = LocalDateTime.now();
		if (DEFAULT_SESSION_TITLE.equals(session.getTitle()))
		{
			String title = content == null ? "" : content.strip();
			session.setTitle(title.length() > 20 ? title.substring(0, 20) : title);
		}
		session.setUpdatedAt(now);
		chatSessionRepository.save(session);

		ChatRecord record = new ChatRecord();
		record.setSessionId(sessionId);
		record.setRole("user");
		record.setContent(content);
		record.setSpotId(spotId);
		record.setCreatedAt(now);
		record = chatRecordRepository.save(record);
		prune(sessionId);
		return record;
	}

	/** 落库助手消息,并更新会话时间。 */
	@Transactional
	public ChatRecord saveAssistantMessage(Long sessionId, String content, Integer spotId)
	{
		ChatSession session = requireSession(sessionId);
		LocalDateTime now = LocalDateTime.now();
		session.setUpdatedAt(now);
		chatSessionRepository.save(session);

		ChatRecord record = new ChatRecord();
		record.setSessionId(sessionId);
		record.setRole("assistant");
		record.setContent(content);
		record.setSpotId(spotId);
		record.setCreatedAt(now);
		record = chatRecordRepository.save(record);
		prune(sessionId);
		return record;
	}

	/** 每会话仅保留最近 MAX_MESSAGES_PER_SESSION 条。 */
	private void prune(Long sessionId)
	{
		List<ChatRecord> latest = chatRecordRepository.findTop10BySessionIdOrderByIdDesc(sessionId);
		if (latest.size() >= MAX_MESSAGES_PER_SESSION)
		{
			List<Long> keepIds = latest.stream()
			                           .limit(MAX_MESSAGES_PER_SESSION)
			                           .map(ChatRecord::getId)
			                           .toList();
			chatRecordRepository.deleteBySessionIdAndIdNotIn(sessionId, keepIds);
		}
	}

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
