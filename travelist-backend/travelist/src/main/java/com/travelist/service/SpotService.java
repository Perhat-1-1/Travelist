package com.travelist.service;

import com.travelist.entity.Spot;
import com.travelist.entity.SpotSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 景点数据服务。现阶段为内置静态数据，后续可替换为数据库实现。
 */
@Service
public class SpotService
{
	private final List<Spot> spots = new ArrayList<>(List.of(
			new Spot(1, "洱海生态廊道", "人气 TOP1", "骑行 · 拍照 · 日落",
					"洱海生态廊道沿大理洱海西岸东西延伸，串联起湿地、村落与田园风光，是环洱海骑行与散步的首选路线。清晨薄雾未散、傍晚夕阳铺满水面时最为出片，适合慢节奏感受大理的风花雪月。",
					List.of("环海自行车骑行", "海舌公园湿地观鸟", "喜洲古镇白族民居", "日落剪影机位丰富"),
					"3-5 月、9-11 月"),
			new Spot(2, "玉龙雪山", "国家 5A", "索道 · 蓝月谷",
					"玉龙雪山位于丽江市北部，十三座雪峰连绵，主峰扇子陡终年积雪，是纳西族心中的神山。乘大索道可抵达 4506 米观景台，山脚蓝月谷湖水呈奇幻的蓝绿色，雪山、冰川、草甸、湖泊在此浓缩。",
					List.of("大索道冰川公园", "蓝月谷徒步", "印象丽江实景演出", "高山杜鹃与雪山同框"),
					"10 月-次年 4 月(雪景最稳定)"),
			new Spot(3, "蜈支洲岛", "潜水圣地", "潜水 · 摩托艇",
					"蜈支洲岛位于三亚海棠湾，海水能见度高、珊瑚资源丰富，被誉为“中国马尔代夫”。岛上有 30 余种水上运动，潜水体验成熟，环岛观景栈道把情人桥、观日岩等景点连成一线，适合度假与亲水活动。",
					List.of("堡礁潜水 / 珊瑚潜水", "摩托艇与动感飞艇", "环岛电瓶车观景", "情人桥与观日岩日落"),
					"11 月-次年 4 月(避暑旺季 7-8 月同样适宜)")));

	public List<SpotSummary> listSummaries()
	{
		return spots.stream()
		            .map(spot -> new SpotSummary(spot.getId(), spot.getName(), spot.getTag(), spot.getDesc()))
		            .toList();
	}

	public Optional<Spot> findById(Integer id)
	{
		if (id == null)
		{
			return Optional.empty();
		}
		return spots.stream().filter(spot -> spot.getId().equals(id)).findFirst();
	}
}
