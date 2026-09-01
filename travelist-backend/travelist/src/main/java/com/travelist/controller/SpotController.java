package com.travelist.controller;

import com.travelist.entity.Result;
import com.travelist.entity.Spot;
import com.travelist.entity.SpotSummary;
import com.travelist.service.SpotService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/spot")
public class SpotController
{
	@Resource
	private SpotService spotService;

	@GetMapping("/list")
	public Result<List<SpotSummary>> list()
	{
		return Result.success(spotService.listSummaries());
	}

	@GetMapping("/{id}")
	public Result<Spot> detail(@PathVariable Integer id)
	{
		Spot spot = spotService.findById(id)
		                       .orElseThrow(() -> new IllegalArgumentException("景点不存在: " + id));
		return Result.success(spot);
	}
}
