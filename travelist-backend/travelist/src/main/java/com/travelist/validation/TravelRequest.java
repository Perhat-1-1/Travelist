package com.travelist.validation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TravelRequest
{
	@NotNull(message = "起始城市不能为空")
	@Size(max = 50, message = "起始城市名称过长")
	private String originCity;
	@NotNull(message = "城市不能为空")
	private String city;
	@NotNull(message = "天数不能为空")
	@Min(value = 1, message = "天数不能小于1")
	@Max(value = 30, message = "天数不能大于30")
	private Integer days;
	@NotNull(message = "预算不能为空")
	@DecimalMin(value = "10", message = "预算不能小于10")
	private Double budget;

	/** 出行类型: one-way 单程 | round-trip 往返(可选,缺省按单程处理) */
	@Pattern(regexp = "one-way|round-trip", message = "tripType 只能是 one-way 或 round-trip")
	private String tripType;
	@Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "出发日期格式应为 yyyy-MM-dd")
	private String departDate;
	@Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "返程日期格式应为 yyyy-MM-dd")
	private String returnDate;
	@Size(max = 200, message = "行程要求不能超过200字")
	private String requirements;
}
