package com.travelist.Util;

import com.travelist.entity.ChatMessage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * LLM 调用工具类。
 * <p>
 * 现阶段默认调用 DeepSeek API（OpenAI 兼容），支持两种调用风格，通过 {@code llm.api-style} 配置：
 * <ul>
 *     <li>{@code responses}（默认）：OpenAI Responses API，POST {@code {base-url}/responses}</li>
 *     <li>{@code chat}：OpenAI Chat Completions，POST {@code {base-url}/chat/completions}</li>
 * </ul>
 * 密钥、地址、模型等均配置在 {@code application.yaml} 的 {@code llm.*} 节点下。
 * <p>
 * 注意：JDK 的 {@link java.net.http.HttpClient} 默认不读取系统代理设置。若本机通过
 * Clash/V2Ray 等代理上网（尤其代理开启了 DNS fake-ip 时），必须在配置中显式指定
 * {@code llm.proxy-host} / {@code llm.proxy-port}，否则会直连代理工具返回的假 IP 导致连接超时。
 */
@Component
public class LLMUtil
{
	public static final String STYLE_RESPONSES = "responses";
	public static final String STYLE_CHAT      = "chat";

	private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
	private static final String DEFAULT_MODEL    = "deepseek-v4-flash-vision-exp";

	private final HttpClient  httpClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	private final String   baseUrl;
	private final String   model;
	private final String   apiKey;
	private final String   apiStyle;
	private final Duration timeout;

	public LLMUtil(
			@Value("${llm.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
			@Value("${llm.model:" + DEFAULT_MODEL + "}") String model,
			@Value("${llm.api-key:}") String apiKey,
			@Value("${llm.api-style:" + STYLE_RESPONSES + "}") String apiStyle,
			@Value("${llm.timeout:60s}") Duration timeout,
			@Value("${llm.connect-timeout:10s}") Duration connectTimeout,
			@Value("${llm.proxy-host:}") String proxyHost,
			@Value("${llm.proxy-port:0}") int proxyPort)
	{
		this.baseUrl = stripTrailingSlash(baseUrl);
		this.model = model;
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.apiStyle = apiStyle == null || apiStyle.isBlank() ? STYLE_RESPONSES : apiStyle.trim();
		this.timeout = timeout;
		this.httpClient = buildHttpClient(connectTimeout, proxyHost, proxyPort);
	}

	/**
	 * 构建 HTTP 客户端。设置了 {@code llm.proxy-host} 时显式走代理，否则直连。
	 */
	private HttpClient buildHttpClient(Duration connectTimeout, String proxyHost, int proxyPort)
	{
		HttpClient.Builder builder = HttpClient.newBuilder()
		                                        .connectTimeout(connectTimeout);
		if (proxyHost != null && !proxyHost.isBlank())
		{
			if (proxyPort <= 0)
			{
				throw new IllegalArgumentException("已配置 llm.proxy-host 但缺少有效的 llm.proxy-port");
			}
			builder = builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost.trim(), proxyPort)));
		}
		return builder.build();
	}

	/**
	 * 仅用用户提问调用 LLM（无系统提示词）。
	 */
	public String complete(String userPrompt)
	{
		return complete(null, userPrompt);
	}

	/**
	 * 按配置的 {@code llm.api-style} 调用 LLM，返回模型生成的文本内容。
	 *
	 * @param systemPrompt 系统提示词，可为 null
	 * @param userPrompt   用户提问
	 * @return 模型返回的文本
	 * @throws LLMException 接口调用失败、响应解析失败或未配置密钥时抛出
	 */
	public String complete(String systemPrompt, String userPrompt)
	{
		if (userPrompt == null || userPrompt.isBlank())
		{
			throw new IllegalArgumentException("userPrompt 不能为空");
		}
		if (this.apiKey.isBlank())
		{
			throw new LLMException("未配置 llm.api-key，请在 application.yaml 的 llm.api-key 或环境变量 DEEPSEEK_API_KEY 中配置");
		}
		if (STYLE_RESPONSES.equalsIgnoreCase(this.apiStyle))
		{
			return completeWithResponses(systemPrompt, userPrompt);
		}
		if (STYLE_CHAT.equalsIgnoreCase(this.apiStyle))
		{
			return completeWithChatCompletions(systemPrompt, userPrompt);
		}
		throw new LLMException("不支持的 llm.api-style: " + this.apiStyle + "（可选值: chat / responses）");
	}

	/**
	 * 按配置的 {@code llm.api-style} 以流式方式调用 LLM，逐段回调增量文本。
	 * <p>
	 * 上游响应协议为 SSE（OpenAI 兼容）：{@code responses} 风格解析
	 * {@code response.output_text.delta} 事件，{@code chat} 风格解析
	 * {@code choices[0].delta.content}。流中每条增量通过 {@code onDelta} 回调，
	 * 正常结束或取消时方法正常返回，异常时抛出 {@link LLMException}。
	 *
	 * @param systemPrompt 系统提示词，可为 null
	 * @param messages     多轮消息(user/assistant)
	 * @param onDelta      增量文本回调
	 * @param cancelled    外部取消标志(置 true 后尽快停止读取上游流)
	 */
	public void streamMessages(String systemPrompt, List<ChatMessage> messages, Consumer<String> onDelta, AtomicBoolean cancelled)
	{
		if (messages == null || messages.isEmpty())
		{
			throw new IllegalArgumentException("messages 不能为空");
		}
		if (this.apiKey.isBlank())
		{
			throw new LLMException("未配置 llm.api-key，请在 application.yaml 的 llm.api-key 或环境变量 DEEPSEEK_API_KEY 中配置");
		}
		if (STYLE_RESPONSES.equalsIgnoreCase(this.apiStyle))
		{
			streamWithResponses(systemPrompt, messages, onDelta, cancelled);
			return;
		}
		if (STYLE_CHAT.equalsIgnoreCase(this.apiStyle))
		{
			streamWithChatCompletions(systemPrompt, messages, onDelta, cancelled);
			return;
		}
		throw new LLMException("不支持的 llm.api-style: " + this.apiStyle + "（可选值: chat / responses）");
	}

	/**
	 * Responses API 流式：POST {base-url}/responses，stream=true。
	 * input 传消息数组（OpenAI 兼容短格式 {role, content}）。
	 */
	private void streamWithResponses(String systemPrompt, List<ChatMessage> messages, Consumer<String> onDelta, AtomicBoolean cancelled)
	{
		ObjectNode payload = objectMapper.createObjectNode();
		payload.put("model", this.model);
		payload.put("stream", true);
		if (systemPrompt != null && !systemPrompt.isBlank())
		{
			payload.put("instructions", systemPrompt);
		}
		ArrayNode input = payload.putArray("input");
		for (ChatMessage message : messages)
		{
			input.addObject()
			     .put("role", message.getRole())
			     .put("content", message.getContent());
		}

		try (BufferedReader reader = openStream(this.baseUrl + "/responses", payload, cancelled))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (cancelled.get())
				{
					return;
				}
				String data = extractSseData(line);
				if (data == null)
				{
					continue;
				}
				JsonNode node;
				try
				{
					node = objectMapper.readTree(data);
				}
				catch (JacksonException e)
				{
					continue; // 忽略非 JSON 行(注释/keepalive)
				}
				String type = node.path("type").asString("");
				if ("response.completed".equals(type))
				{
					return;
				}
				if ("response.failed".equals(type))
				{
					throw new LLMException("LLM 响应失败: " + node.path("error").path("message").asString("未知错误"));
				}
				if ("response.output_text.delta".equals(type))
				{
					String delta = node.path("delta").asString("");
					if (!delta.isEmpty())
					{
						onDelta.accept(delta);
					}
				}
			}
		}
		catch (IOException e)
		{
			throw new LLMException("读取 LLM 流式响应失败: " + this.baseUrl + "/responses（" + e.getMessage() + "）", e);
		}
	}

	/**
	 * Chat Completions 流式：POST {base-url}/chat/completions，stream=true。
	 */
	private void streamWithChatCompletions(String systemPrompt, List<ChatMessage> messages, Consumer<String> onDelta, AtomicBoolean cancelled)
	{
		ObjectNode payload = objectMapper.createObjectNode();
		payload.put("model", this.model);
		payload.put("stream", true);
		ArrayNode arr = payload.putArray("messages");
		if (systemPrompt != null && !systemPrompt.isBlank())
		{
			arr.addObject()
			   .put("role", "system")
			   .put("content", systemPrompt);
		}
		for (ChatMessage message : messages)
		{
			arr.addObject()
			   .put("role", message.getRole())
			   .put("content", message.getContent());
		}

		try (BufferedReader reader = openStream(this.baseUrl + "/chat/completions", payload, cancelled))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (cancelled.get())
				{
					return;
				}
				String data = extractSseData(line);
				if (data == null)
				{
					continue;
				}
				if ("[DONE]".equals(data))
				{
					return;
				}
				try
				{
					JsonNode node = objectMapper.readTree(data);
					JsonNode content = node.path("choices").path(0).path("delta").path("content");
					if (content.isTextual())
					{
						String delta = content.asString();
						if (!delta.isEmpty())
						{
							onDelta.accept(delta);
						}
					}
				}
				catch (JacksonException e)
				{
					// 忽略无法解析的行
				}
			}
		}
		catch (IOException e)
		{
			throw new LLMException("读取 LLM 流式响应失败: " + this.baseUrl + "/chat/completions（" + e.getMessage() + "）", e);
		}
	}

	/**
	 * 发送流式 POST 请求并返回响应体的行读取器。
	 * 非 2xx 状态码、连接失败都会抛出 {@link LLMException}。
	 * 调用方负责关闭返回的 reader(关闭即中止上游连接)。
	 */
	private BufferedReader openStream(String url, ObjectNode payload, AtomicBoolean cancelled)
	{
		HttpRequest request = HttpRequest.newBuilder()
		                                 .uri(URI.create(url))
		                                 .timeout(this.timeout)
		                                 .header("Content-Type", "application/json")
		                                 .header("Accept", "text/event-stream")
		                                 .header("Authorization", "Bearer " + this.apiKey)
		                                 .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
		                                 .build();
		try
		{
			HttpResponse<InputStream> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() < 200 || response.statusCode() >= 300)
			{
				String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
				JsonNode parsed = parseBody(body);
				String errorMessage = parsed != null ? extractErrorMessage(parsed) : abbreviate(body);
				throw new LLMException("LLM 接口调用失败（HTTP " + response.statusCode() + "）: " + errorMessage);
			}
			return new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			String hint = e instanceof ConnectException
					? "；连接被拒或超时，如本机使用 Clash/V2Ray 等代理请配置 llm.proxy-host / llm.proxy-port（JDK HttpClient 默认不读系统代理）"
					: "";
			throw new LLMException("调用 LLM 接口失败: " + url + "（" + e.getMessage() + "）" + hint, e);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new LLMException("调用 LLM 接口被中断: " + url, e);
		}
	}

	/** 从 SSE 行中取出 data 负载；非 data 行或空负载返回 null。 */
	private String extractSseData(String line)
	{
		if (line == null || !line.startsWith("data:"))
		{
			return null;
		}
		String data = line.substring(5).trim();
		return data.isEmpty() ? null : data;
	}


	/**
	 * OpenAI Responses API：POST {base-url}/responses
	 */
	private String completeWithResponses(String systemPrompt, String userPrompt)
	{		ObjectNode payload = objectMapper.createObjectNode();
		payload.put("model", this.model);
		payload.put("stream", false);
		if (systemPrompt != null && !systemPrompt.isBlank())
		{
			payload.put("instructions", systemPrompt);
		}
		payload.put("input", userPrompt);

		JsonNode response = postJson(this.baseUrl + "/responses", payload);
		JsonNode output = response.path("output");
		if (!output.isArray())
		{
			throw new LLMException("Responses API 响应缺少 output 数组: " + response);
		}

		StringBuilder text = new StringBuilder();
		for (JsonNode item : output)
		{
			if ("reasoning".equals(item.path("type").asString()))
			{
				continue; // 跳过推理内容
			}
			appendTextValues(item, text);
		}
		String result = text.toString().trim();
		if (result.isEmpty())
		{
			throw new LLMException("Responses API 响应没有任何文本输出: " + response);
		}
		return result;
	}

	/**
	 * OpenAI Chat Completions：POST {base-url}/chat/completions
	 */
	private String completeWithChatCompletions(String systemPrompt, String userPrompt)
	{
		ObjectNode payload = objectMapper.createObjectNode();
		payload.put("model", this.model);
		payload.put("stream", false);
		ArrayNode messages = payload.putArray("messages");
		if (systemPrompt != null && !systemPrompt.isBlank())
		{
			messages.addObject()
			        .put("role", "system")
			        .put("content", systemPrompt);
		}
		messages.addObject()
		        .put("role", "user")
		        .put("content", userPrompt);

		JsonNode response = postJson(this.baseUrl + "/chat/completions", payload);
		JsonNode choices = response.path("choices");
		if (!choices.isArray() || choices.isEmpty())
		{
			throw new LLMException("Chat Completions 响应缺少 choices: " + response);
		}
		String content = choices.get(0).path("message").path("content").asString("");
		if (content.isBlank())
		{
			throw new LLMException("Chat Completions 响应内容为空: " + response);
		}
		return content;
	}

	/**
	 * 发送 POST JSON 请求并解析响应体。非 2xx 状态码或非 JSON 响应都会抛出 {@link LLMException}。
	 */
	private JsonNode postJson(String url, ObjectNode payload)
	{
		HttpRequest request = HttpRequest.newBuilder()
		                                 .uri(URI.create(url))
		                                 .timeout(this.timeout)
		                                 .header("Content-Type", "application/json")
		                                 .header("Authorization", "Bearer " + this.apiKey)
		                                 .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
		                                 .build();
		try
		{
			HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			JsonNode body = parseBody(response.body());
			if (response.statusCode() < 200 || response.statusCode() >= 300)
			{
				String errorMessage = body != null ? extractErrorMessage(body) : abbreviate(response.body());
				throw new LLMException("LLM 接口调用失败（HTTP " + response.statusCode() + "）: " + errorMessage);
			}
			if (body == null)
			{
				throw new LLMException("LLM 接口返回了非 JSON 响应: " + abbreviate(response.body()));
			}
			return body;
		}
		catch (IOException e)
		{
			String hint = e instanceof ConnectException
					? "；连接被拒或超时，如本机使用 Clash/V2Ray 等代理请配置 llm.proxy-host / llm.proxy-port（JDK HttpClient 默认不读系统代理）"
					: "";
			throw new LLMException("调用 LLM 接口失败: " + url + "（" + e.getMessage() + "）" + hint, e);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new LLMException("调用 LLM 接口被中断: " + url, e);
		}
	}

	/**
	 * 递归收集响应中的文本节点。
	 * 兼容两种结构：message 类型条目下的 content[].text，以及 output_text 类型条目直接携带的 text。
	 */
	private void appendTextValues(JsonNode node, StringBuilder builder)
	{
		if (node == null || node.isNull() || node.isValueNode())
		{
			return;
		}
		if (node.isArray())
		{
			for (JsonNode child : node)
			{
				appendTextValues(child, builder);
			}
			return;
		}
		JsonNode textNode = node.get("text");
		if (textNode != null && textNode.isString())
		{
			String value = textNode.asString();
			if (!value.isBlank())
			{
				if (!builder.isEmpty())
				{
					builder.append('\n');
				}
				builder.append(value);
			}
		}
		node.forEach(child -> appendTextValues(child, builder));
	}

	private JsonNode parseBody(String body)
	{
		if (body == null || body.isBlank())
		{
			return null;
		}
		try
		{
			return this.objectMapper.readTree(body);
		}
		catch (JacksonException e)
		{
			return null;
		}
	}

	private String extractErrorMessage(JsonNode body)
	{
		JsonNode message = body.path("error").path("message");
		if (message.isString() && !message.asString().isBlank())
		{
			return message.asString();
		}
		JsonNode error = body.path("error");
		return error.isString() ? error.asString() : body.toString();
	}

	private String abbreviate(String text)
	{
		if (text == null || text.isBlank())
		{
			return "(empty)";
		}
		return text.length() > 200 ? text.substring(0, 200) + "..." : text;
	}

	private String stripTrailingSlash(String url)
	{
		if (url == null || url.isBlank())
		{
			return DEFAULT_BASE_URL;
		}
		String trimmed = url.trim();
		while (trimmed.endsWith("/"))
		{
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}
}
