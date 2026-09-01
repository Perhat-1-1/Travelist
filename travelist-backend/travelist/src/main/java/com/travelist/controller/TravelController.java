package com.travelist.controller;

import com.travelist.entity.Result;
import com.travelist.entity.TravelPlanRecommend;
import com.travelist.service.TravelService;
import com.travelist.validation.TravelRequest;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/travel")
public class TravelController
{
	@Resource
	private TravelService travelService;
	
	@PostMapping("/recommend")
	public Result<TravelPlanRecommend> recommend(@Valid @RequestBody TravelRequest travelRequest)
	{
		TravelPlanRecommend recommend = travelService.recommend(travelRequest);
		return Result.success(recommend);
	}
}
