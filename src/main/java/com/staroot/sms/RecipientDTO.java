package com.staroot.sms;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipientDTO {
    private String email;
    private String content;
    private boolean success;
    private String errorMessage;
    private Map<String, String> customData;
}
