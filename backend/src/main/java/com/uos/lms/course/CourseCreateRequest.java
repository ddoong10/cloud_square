package com.uos.lms.course;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourseCreateRequest(
        @NotBlank(message = "Course title is required")
        @Size(max = 255, message = "Course title must be 255 chars or less")
        String title,

        String description,

        @Size(max = 2048, message = "Thumbnail URL must be 2048 chars or less")
        String thumbnailUrl,

        @Size(max = 100, message = "Category must be 100 chars or less")
        String category,

        @Min(value = 1, message = "Pass criteria must be at least 1")
        @Max(value = 100, message = "Pass criteria must be at most 100")
        Integer passCriteria
) {
}
