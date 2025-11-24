package com.mathspeed.domain.port;

import com.mathspeed.domain.model.Quiz;

import java.util.List;

public interface QuizzRepository {
    int getQuizCount();
    List<Quiz> getAllQuizzes();
    List<Quiz> getOwnQuizzes(String playerId);
}
