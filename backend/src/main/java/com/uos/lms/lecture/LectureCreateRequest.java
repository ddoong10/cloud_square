package com.uos.lms.lecture;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LectureCreateRequest(
        @NotBlank(message = "Lecture title is required")
        @Size(max = 255, message = "Lecture title must be 255 chars or less")
        String title,

        @NotBlank(message = "Video URL is required")
        @Size(max = 2048, message = "Video URL must be 2048 chars or less")
        String videoUrl,

        @Size(max = 2048, message = "Thumbnail URL must be 2048 chars or less")
        String thumbnailUrl
) {
}
