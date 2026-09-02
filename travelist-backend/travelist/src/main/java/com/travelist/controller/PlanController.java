package com.travelist.controller;

import com.travelist.entity.Result;
import com.travelist.entity.TravelPlanRecommend;
import com.travelist.service.TravelService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 历史规划查询(保存的规划从数据库读取)。
 */
@RestController
@RequestMapping("/api/plan")
public class PlanController
{
	@Resource
	private TravelService travelService;

	@GetMapping("/list")
	public Result<List<Map<String, Object>>> list()
	{
		return Result.success(travelService.listPlans());
	}

	@GetMapping("/{id}")
	public Result<TravelPlanRecommend> detail(@PathVariable Long id)
	{
		return Result.success(travelService.getPlan(id));
	}
}
