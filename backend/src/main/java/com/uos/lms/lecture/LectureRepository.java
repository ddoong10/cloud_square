package com.uos.lms.lecture;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LectureRepository extends JpaRepository<Lecture, Long> {
    List<Lecture> findAllByOrderByCreatedAtDesc();

    boolean existsByVideoUrl(String videoUrl);

    boolean existsByVideoUrlHash(String videoUrlHash);
}
