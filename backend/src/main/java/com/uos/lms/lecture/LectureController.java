package com.uos.lms.lecture;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @DeleteMapping("/{lectureId}")
    public LectureDeleteResponse delete(@PathVariable Long lectureId) {
        return lectureService.delete(lectureId);
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
