package com.rapidmart.user.repository;

import com.rapidmart.common.enums.Role;
import com.rapidmart.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByRolesContaining(Role role);

    @Query("""
            select distinct u from User u join u.roles r
            where r = :role
              and (lower(u.fullName) like lower(concat('%', :term, '%'))
                   or lower(u.email) like lower(concat('%', :term, '%')))
            order by u.fullName
            """)
    List<User> searchByRoleAndNameOrEmail(@Param("role") Role role, @Param("term") String term, Pageable pageable);
}
