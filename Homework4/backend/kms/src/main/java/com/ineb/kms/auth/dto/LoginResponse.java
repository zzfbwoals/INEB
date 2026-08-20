package com.ineb.kms.auth.dto;

public record LoginResponse(String accessToken, String name, String role) {
}
