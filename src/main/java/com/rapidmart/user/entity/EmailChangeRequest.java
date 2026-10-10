package com.rapidmart.user.entity;

import com.rapidmart.common.entity.BaseEntity;
import com.rapidmart.common.enums.EmailChangeStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "email_change_requests")
public class EmailChangeRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "new_email", nullable = false)
    private String newEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailChangeStatus status = EmailChangeStatus.PENDING;

    public EmailChangeRequest() {
    }

    public EmailChangeRequest(User user, String newEmail) {
        this.user = user;
        this.newEmail = newEmail;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getNewEmail() {
        return newEmail;
    }

    public EmailChangeStatus getStatus() {
        return status;
    }

    public void setStatus(EmailChangeStatus status) {
        this.status = status;
    }
}
