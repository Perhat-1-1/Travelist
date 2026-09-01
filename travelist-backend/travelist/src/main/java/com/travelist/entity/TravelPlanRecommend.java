package com.travelist.entity;

import lombok.Data;
import java.util.List;

@Data
public class TravelPlanRecommend
{
	private Boolean               success;
	private String                city;
	private Integer               days;
	private Double                totalBudget;
	private List<DailyItinerary>  dailyItineraryList;
	private BudgetBreakdown       budgetBreakdown;
	private TransportPlan         transportPlan;
	private List<String>          tips;
	private List<String>          warnings;
	
	@Data
	public static class DailyItinerary
	{
		private Integer  day;
		private String   date;
		private TimeSlot morning;
		private TimeSlot afternoon;
		private TimeSlot evening;
	}
	
	@Data
	public static class TimeSlot
	{
		private String spot;
		private String duration;
		private String transportation;
		private String description;
	}

	/** 简易交通流程:去程/市内/返程(返程仅往返模式有)。 */
	@Data
	public static class TransportPlan
	{
		private String toDestination; // 去程:起始城市 → 目的地(含方式与大致时长)
		private String local;         // 目的地市内与景点间交通概述
		private String returnRoute;   // 返程:目的地 → 起始城市(往返模式必填)
	}
	
	@Data
	public static class BudgetBreakdown
	{
		private Double accommodation;
		private Double food;
		private Double transportation;
		private Double tickets;
		private Double other;
	}
}
