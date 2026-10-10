package com.rapidmart.user.controller;

import com.rapidmart.user.dto.ChangePasswordRequest;
import com.rapidmart.user.dto.EmailChangeRequestBody;
import com.rapidmart.user.dto.EmailChangeRequestDto;
import com.rapidmart.user.dto.UpdateProfileRequest;
import com.rapidmart.user.dto.UserResponseDto;
import com.rapidmart.user.service.ManagerProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/manager/profile")
@PreAuthorize("hasRole('MANAGER')")
public class ManagerProfileController {

    private final ManagerProfileService profileService;

    public ManagerProfileController(ManagerProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<UserResponseDto> getProfile() {
        return ResponseEntity.ok(profileService.getProfile());
    }

    @PutMapping
    public ResponseEntity<UserResponseDto> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(profileService.updateProfile(request));
    }

    /** The pending email-change request, or 204 when there is none. */
    @GetMapping("/email-change")
    public ResponseEntity<EmailChangeRequestDto> getPendingEmailChange() {
        return profileService.getPendingEmailChange()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/email-change")
    public ResponseEntity<EmailChangeRequestDto> requestEmailChange(@Valid @RequestBody EmailChangeRequestBody request) {
        return new ResponseEntity<>(profileService.requestEmailChange(request.newEmail()), HttpStatus.CREATED);
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(request);
        return ResponseEntity.noContent().build();
    }
}
