package com.travelist.service;

import com.travelist.repository.TravelPlanRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.travelist.Util.LLMUtil;
import com.travelist.entity.TravelPlan;
import com.travelist.entity.TravelPlanRecommend;
import com.travelist.validation.TravelRequest;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class TravelService
{
	private static final String SYSTEM_PROMPT = """
			你是一位资深的旅游规划师。用户会提供起始城市、目的地城市、天数和总预算。
			请只输出一个 JSON 对象作为行程规划结果，不要输出任何解释文字，也不要使用 markdown 代码块。
			JSON 必须严格遵循以下结构（示例）：

			{
			  "success": true,
			  "city": "上海",
			  "days": 3,
			  "totalBudget": 5000.0,
			  "dailyItineraryList": [
			    {
			      "day": 1,
			      "date": "第1天",
			      "morning": {"spot": "景点名称", "duration": "2小时", "transportation": "地铁", "description": "一句话描述"},
			      "afternoon": {"spot": "景点名称", "duration": "2小时", "transportation": "步行", "description": "一句话描述"},
			      "evening": {"spot": "景点名称", "duration": "2小时", "transportation": "打车", "description": "一句话描述"}
			    }
			  ],
			  "budgetBreakdown": {"accommodation": 1000.0, "food": 1000.0, "transportation": 500.0, "tickets": 500.0, "other": 0.0},
			  "transportPlan": {
			    "toDestination": "从起始城市到目的地的去程交通路线，含交通方式与大致时长（如：高铁D1至大理,约4小时）",
			    "local": "目的地市内及景点间的主要交通方式概述",
			    "returnRoute": "行程结束后的返程路线（往返模式必填：从目的地返回起始城市，含方式与时长；单程模式可省略）"
			  },
			  "tips": ["实用建议"],
			  "warnings": ["注意事项"]
			}

			要求：
			1. 所有字段名必须与上述结构完全一致，不要新增或删除字段。
			2. dailyItineraryList 的长度必须等于用户要求的天数。
			3. budgetBreakdown 各项之和不超过 totalBudget，且所有金额为数字。
			4. tips 和 warnings 不能为空数组。
			5. 景点和行程应符合该城市的真实情况，使用中文描述。
			6. transportPlan 必须给出，其中 toDestination 的起点必须是用户提供的起始城市。
			7. 往返模式下 transportPlan.returnRoute 必填，应描述行程结束后从目的地返回起始城市的路线(交通方式与大致时长)。
			""";

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Resource
	private LLMUtil llmUtil;

	@Resource
	private TravelPlanRepository travelPlanRepository;

	/**
	 * 调用 LLM 生成推荐计划并返回结果。
	 *
	 * @param request 规划请求(city/days/budget + 可选出行信息)
	 * @return 解析后的推荐计划(已保存到数据库)
	 */
	@Transactional
	public TravelPlanRecommend recommend(TravelRequest request)
	{
		String city = request.getCity();
		Integer days = request.getDays();
		Double budget = request.getBudget();

		StringBuilder prompt = new StringBuilder();
		prompt.append(String.format("请为城市「%s」规划 %d 天的旅游行程，总预算 %.0f 元。", city, days, budget));
		if (request.getOriginCity() != null && !request.getOriginCity().isBlank())
		{
			prompt.append("起始城市:「").append(request.getOriginCity().trim()).append("」。");
		}
		if ("round-trip".equals(request.getTripType()))
		{
			prompt.append("出行类型:往返");
			if (request.getDepartDate() != null)
			{
				prompt.append(";出发日期:").append(request.getDepartDate());
			}
			if (request.getReturnDate() != null)
			{
				prompt.append(";返程日期:").append(request.getReturnDate());
			}
			prompt.append('。');
			prompt.append("行程结束后需返回起始城市,请在 transportPlan.returnRoute 中给出返程路线。");
		}
		else if (request.getDepartDate() != null)
		{
			prompt.append("出行类型:单程;出发日期:").append(request.getDepartDate()).append("。");
		}
		if (request.getRequirements() != null && !request.getRequirements().isBlank())
		{
			prompt.append("行程要求:").append(request.getRequirements().trim()).append('。');
		}

		String llmText = llmUtil.complete(SYSTEM_PROMPT, prompt.toString());
		TravelPlanRecommend recommend = parseRecommend(llmText, city, days, budget);
		return savePlan(request, recommend);
	}

	/** 保存规划到数据库,并把 planId 回填到结果对象。 */
	private TravelPlanRecommend savePlan(TravelRequest request, TravelPlanRecommend recommend)
	{
		TravelPlan entity = new TravelPlan();
		entity.setOriginCity(request.getOriginCity());
		entity.setCity(recommend.getCity());
		entity.setTripType(request.getTripType());
		entity.setDepartDate(request.getDepartDate());
		entity.setReturnDate(request.getReturnDate());
		entity.setBudget(request.getBudget());
		entity.setDays(request.getDays());
		entity.setRequirements(request.getRequirements());
		try
		{
			entity.setPlanJson(objectMapper.writeValueAsString(recommend));
		}
		catch (JacksonException e)
		{
			throw new IllegalStateException("序列化规划结果失败", e);
		}
		entity.setCreatedAt(LocalDateTime.now());
		travelPlanRepository.save(entity);
		recommend.setPlanId(entity.getId());
		return recommend;
	}

	/** 最近保存的规划列表(元数据,不含完整 JSON)。 */
	@Transactional(readOnly = true)
	public List<Map<String, Object>> listPlans()
	{
		return travelPlanRepository.findTop10ByOrderByIdDesc().stream()
		                           .map(p -> Map.<String, Object>of(
				                           "id", p.getId(),
				                           "originCity", p.getOriginCity(),
				                           "city", p.getCity(),
				                           "days", p.getDays(),
				                           "tripType", p.getTripType(),
				                           "budget", p.getBudget(),
				                           "createdAt", p.getCreatedAt() == null ? "" : p.getCreatedAt().toString()))
		                           .toList();
	}

	/** 读取某条保存的规划完整内容。 */
	@Transactional(readOnly = true)
	public TravelPlanRecommend getPlan(Long id)
	{
		TravelPlan plan = travelPlanRepository.findById(id)
		                                      .orElseThrow(() -> new IllegalArgumentException("规划不存在: " + id));
		try
		{
			TravelPlanRecommend recommend = objectMapper.readValue(plan.getPlanJson(), TravelPlanRecommend.class);
			recommend.setPlanId(plan.getId());
			return recommend;
		}
		catch (JacksonException e)
		{
			throw new IllegalStateException("读取保存的规划失败: " + id, e);
		}
	}

	private TravelPlanRecommend parseRecommend(String llmText, String city, Integer days, Double budget)
	{
		String json = stripCodeFence(llmText);
		TravelPlanRecommend recommend;
		try
		{
			recommend = objectMapper.readValue(json, TravelPlanRecommend.class);
		}
		catch (JacksonException e)
		{
			throw new IllegalStateException("无法解析 LLM 返回的 JSON 结果: " + abbreviate(json), e);
		}
		if (recommend == null)
		{
			throw new IllegalStateException("LLM 未返回有效的结果对象: " + abbreviate(json));
		}
		fillDefaults(recommend, city, days, budget);
		return recommend;
	}

	/** 兼容模型偶尔输出 markdown 代码块的情况。 */
	private String stripCodeFence(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.trim()
		           .replaceAll("^```[\\w-]*\\s*", "")
		           .replaceAll("\\s*```$", "")
		           .trim();
	}

	/** 模型漏填的字段用请求参数兜底，保证返回结构完整。 */
	private void fillDefaults(TravelPlanRecommend recommend, String city, Integer days, Double budget)
	{
		if (recommend.getSuccess() == null)
		{
			recommend.setSuccess(true);
		}
		if (recommend.getCity() == null)
		{
			recommend.setCity(city);
		}
		if (recommend.getDays() == null)
		{
			recommend.setDays(days);
		}
		if (recommend.getTotalBudget() == null)
		{
			recommend.setTotalBudget(budget);
		}
	}

	private String abbreviate(String text)
	{
		if (text == null || text.isBlank())
		{
			return "(empty)";
		}
		return text.length() > 500 ? text.substring(0, 500) + "..." : text;
	}
}
