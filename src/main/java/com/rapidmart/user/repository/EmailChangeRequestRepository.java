package com.rapidmart.user.repository;

import com.rapidmart.common.enums.EmailChangeStatus;
import com.rapidmart.user.entity.EmailChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailChangeRequestRepository extends JpaRepository<EmailChangeRequest, Long> {

    Optional<EmailChangeRequest> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, EmailChangeStatus status);

    List<EmailChangeRequest> findByUserIdAndStatus(Long userId, EmailChangeStatus status);
}
