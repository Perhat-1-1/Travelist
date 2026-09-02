package com.travelist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 景点(MySQL 持久化)。API 响应结构保持不变,不含任何价格字段。
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "spot")
public class Spot
{
	@Id
	private Integer id;
	private String  name;
	private String  tag;
	/** DB 列名用 spot_desc,避免 desc 是 MySQL 关键字;JSON 字段名仍为 desc */
	@Column(name = "spot_desc")
	private String  desc;   // 一句话简介
	@Column(columnDefinition = "TEXT")
	private String  detail; // 详情段落
	/** JSON 数组文本,如 ["环海自行车骑行","海舌公园湿地观鸟"] */
	@Convert(converter = StringListConverter.class)
	@Column(columnDefinition = "TEXT")
	private List<String> highlights;
	@Column(name = "best_season")
	private String  bestSeason;

	public Spot(Integer id, String name, String tag, String desc, String detail, List<String> highlights, String bestSeason)
	{
		this.id = id;
		this.name = name;
		this.tag = tag;
		this.desc = desc;
		this.detail = detail;
		this.highlights = highlights;
		this.bestSeason = bestSeason;
	}
}
