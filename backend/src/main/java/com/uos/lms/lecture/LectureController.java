package com.uos.lms.lecture;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lectures")
@RequiredArgsConstructor
public class LectureController {

    private final LectureService lectureService;
    private final ObjectProvider<LectureSyncService> lectureSyncServiceProvider;

    @GetMapping
    public List<LectureResponse> list(@RequestParam(required = false) Long courseId) {
        if (courseId != null) {
            return lectureService.listByCourse(courseId);
        }
        return lectureService.list();
    }

    @PostMapping
    public LectureResponse create(@Valid @RequestBody LectureCreateRequest request) {
        return lectureService.create(request);
    }

    @PutMapping("/{lectureId}")
    public LectureResponse update(@PathVariable Long lectureId, @Valid @RequestBody LectureUpdateRequest request) {
        return lectureService.update(lectureId, request);
    }

    @DeleteMapping("/{lectureId}")
    public LectureDeleteResponse delete(@PathVariable Long lectureId) {
        return lectureService.delete(lectureId);
    }

    @GetMapping("/{lectureId}/stream-url")
    public StreamUrlResponse streamUrl(@PathVariable Long lectureId, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getPrincipal().toString());
        return lectureService.getStreamUrl(lectureId, userId, authentication);
    }

    @GetMapping("/{lectureId}/crypto-check")
    public LectureCryptoCheckResponse cryptoCheck(@PathVariable Long lectureId) {
        return lectureService.checkCrypto(lectureId);
    }

    @PostMapping("/sync")
    public LectureSyncResponse sync() {
        LectureSyncService lectureSyncService = lectureSyncServiceProvider.getIfAvailable();
        if (lectureSyncService == null) {
            throw new IllegalStateException("Lecture sync is unavailable in this profile");
        }
        return lectureSyncService.syncFromObjectStorage();
    }
}
