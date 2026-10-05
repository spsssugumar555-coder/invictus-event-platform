package com.invictus.platform.repository;

import com.invictus.platform.entity.Question;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    Optional<Question> findByNumber(Integer number);
    List<Question> findAllByOrderByNumberAsc();

    Optional<Question> findTopByOrderByNumberDesc();
}
