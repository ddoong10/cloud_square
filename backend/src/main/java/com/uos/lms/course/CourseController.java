package com.uos.lms.course;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public List<CourseResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "false") boolean all
    ) {
        if (search != null && !search.isBlank()) {
            return courseService.search(search.trim());
        }
        if (category != null && !category.isBlank()) {
            return courseService.listByCategory(category.trim());
        }
        if (all) {
            return courseService.listAll();
        }
        return courseService.listPublished();
    }

    @GetMapping("/{courseId}")
    public CourseResponse getById(@PathVariable Long courseId) {
        return courseService.getById(courseId);
    }

    @PostMapping
    public CourseResponse create(@Valid @RequestBody CourseCreateRequest request, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return courseService.create(request, userId);
    }

    @PutMapping("/{courseId}")
    public CourseResponse update(@PathVariable Long courseId, @Valid @RequestBody CourseUpdateRequest request,
                                 Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return courseService.update(courseId, request, userId, hasAdminRole(authentication));
    }

    @DeleteMapping("/{courseId}")
    public void delete(@PathVariable Long courseId, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        courseService.delete(courseId, userId, hasAdminRole(authentication));
    }

    private boolean hasAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
