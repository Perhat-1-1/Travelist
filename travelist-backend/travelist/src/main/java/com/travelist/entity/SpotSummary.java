package com.travelist.entity;

import lombok.Data;

/**
 * 景点摘要(首页热门景点列表用)。
 */
@Data
public class SpotSummary
{
	private Integer id;
	private String  name;
	private String  tag;
	private String  desc; // 一句话简介

	public SpotSummary(Integer id, String name, String tag, String desc)
	{
		this.id = id;
		this.name = name;
		this.tag = tag;
		this.desc = desc;
	}
}
