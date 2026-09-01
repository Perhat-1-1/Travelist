package com.travelist.entity;

import lombok.Data;

import java.util.List;

/**
 * 景点详情(详情页 + AI 聊天上下文用)。不含任何价格字段。
 */
@Data
public class Spot
{
	private Integer      id;
	private String       name;
	private String       tag;
	private String       desc;   // 一句话简介
	private String       detail; // 详情段落
	private List<String> highlights;
	private String       bestSeason;

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
