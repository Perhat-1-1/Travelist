package com.travelist.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ChatRecordRepository extends JpaRepository<ChatRecord, Long>
{
	List<ChatRecord> findTop10BySessionIdOrderByIdDesc(Long sessionId);

	long countBySessionId(Long sessionId);

	void deleteBySessionId(Long sessionId);

	void deleteBySessionIdAndIdNotIn(Long sessionId, Collection<Long> keepIds);
}
