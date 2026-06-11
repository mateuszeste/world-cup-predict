package com.worldcup.repository;

import com.worldcup.model.Prediction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    List<Prediction> findByUsername(String username);

    Optional<Prediction> findByUsernameAndMatchId(String username, Long matchId);

    List<Prediction> findByMatchId(Long matchId);
}
