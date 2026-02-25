package com.uos.lms.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateNameRequest(
        @NotBlank
        @Size(max = 50, message = "이름은 50자 이내로 입력해주세요")
        @Pattern(regexp = "^[^<>&\"'`;/\\\\(){}]*$", message = "이름에 특수문자(<, >, &, \", ' 등)는 사용할 수 없습니다")
        String name
) {}
