package com.uos.lms.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByPublishedTrueOrderByCreatedAtDesc();

    List<Course> findByInstructorIdOrderByCreatedAtDesc(Long instructorId);

    List<Course> findByCategoryAndPublishedTrueOrderByCreatedAtDesc(String category);

    List<Course> findByTitleContainingIgnoreCaseAndPublishedTrue(String keyword);
}
