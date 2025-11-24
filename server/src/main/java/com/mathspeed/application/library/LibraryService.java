package com.mathspeed.application.library;

import com.mathspeed.domain.model.Quiz;
import com.mathspeed.domain.port.QuizzRepository;

import java.util.List;

public class LibraryService {
    private final QuizzRepository quizzRepository;

    public LibraryService(QuizzRepository quizzRepository) {
        this.quizzRepository = quizzRepository;
    }

    public List<Quiz> listAllQuizzes() {
        return quizzRepository.getAllQuizzes();
    }

    public List<Quiz> listOwnQuizzes(String requesterId) {
        if (requesterId == null || requesterId.isEmpty()) return java.util.Collections.emptyList();
        return quizzRepository.getOwnQuizzes(requesterId);
    }
}
