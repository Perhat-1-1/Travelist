package com.travelist.repository;

import com.travelist.entity.TravelPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelPlanRepository extends JpaRepository<TravelPlan, Long>
{
	List<TravelPlan> findTop10ByOrderByIdDesc();
}
