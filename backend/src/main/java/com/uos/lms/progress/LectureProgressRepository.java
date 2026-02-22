package com.uos.lms.progress;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface LectureProgressRepository extends JpaRepository<LectureProgress, Long> {

    Optional<LectureProgress> findByUserIdAndLectureId(Long userId, Long lectureId);

    long countByUserIdAndLectureIdInAndCompletedTrue(Long userId, Collection<Long> lectureIds);
}
