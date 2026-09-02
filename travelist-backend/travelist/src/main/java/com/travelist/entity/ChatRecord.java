package com.travelist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 聊天消息记录。每个会话表内仅保留最近 10 条(超出裁剪)。
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "chat_message", indexes = @Index(name = "idx_chat_session_created", columnList = "session_id, id"))
public class ChatRecord
{
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "session_id")
	private Long sessionId;

	/** user | assistant */
	private String role;

	@Column(columnDefinition = "TEXT")
	private String content;

	/** 该轮使用的景点上下文 id(可空) */
	@Column(name = "spot_id")
	private Integer spotId;

	@Column(name = "created_at")
	private LocalDateTime createdAt;
}
