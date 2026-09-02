package com.travelist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 保存的行程规划:元数据列 + 完整规划 JSON。
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "travel_plan")
public class TravelPlan
{
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "origin_city")
	private String originCity;
	private String  city;
	@Column(name = "trip_type")
	private String  tripType;
	@Column(name = "depart_date")
	private String  departDate;
	@Column(name = "return_date")
	private String  returnDate;
	private Double  budget;
	private Integer days;
	private String  requirements;
	/** TravelPlanRecommend 完整 JSON */
	@Column(name = "plan_json", columnDefinition = "LONGTEXT")
	private String  planJson;
	@Column(name = "created_at")
	private LocalDateTime createdAt;
}
