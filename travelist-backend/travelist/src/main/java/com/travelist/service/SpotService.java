package com.travelist.service;

import com.travelist.entity.Spot;
import com.travelist.entity.SpotSummary;
import com.travelist.repository.SpotRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 景点数据服务(MySQL 读取)。
 */
@Service
public class SpotService
{
	@Resource
	private SpotRepository spotRepository;

	public List<SpotSummary> listSummaries()
	{
		return spotRepository.findAllByOrderByIdAsc().stream()
		                     .map(spot -> new SpotSummary(spot.getId(), spot.getName(), spot.getTag(), spot.getDesc()))
		                     .toList();
	}

	public Optional<Spot> findById(Integer id)
	{
		if (id == null)
		{
			return Optional.empty();
		}
		return spotRepository.findById(id);
	}
}
