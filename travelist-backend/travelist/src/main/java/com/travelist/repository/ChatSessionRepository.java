package com.travelist.repository;

import com.travelist.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long>
{
	List<ChatSession> findTop20ByOrderByUpdatedAtDesc();

	List<ChatSession> findAllByOrderByUpdatedAtAsc();
}
