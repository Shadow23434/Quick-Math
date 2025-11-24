package com.mathspeed.infrastructure.persistence;

import com.mathspeed.domain.model.Quiz;
import com.mathspeed.domain.port.QuizzRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class QuizDAOImpl extends BaseDAO implements QuizzRepository {
    public QuizDAOImpl() {super();}

    @Override
    public int getQuizCount() {
        String sql = "SELECT COUNT(*) AS total FROM quizzes";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("total");
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching quiz count", e);
        }
    }

    @Override
    public List<Quiz> getAllQuizzes() {
        String sql = "SELECT * FROM quizzes ORDER BY created_at DESC";
        List<Quiz> quizzes = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Quiz quiz = new Quiz();
                quiz.setId(rs.getString("id"));
                quiz.setTitle(rs.getString("title"));
                quiz.setQuestionNumber(rs.getInt("question_number"));
                quiz.setPlayerId(rs.getString("player_id"));

                String level = rs.getString("level");
                if (level != null) quiz.setLevel(level);

                Timestamp ts = rs.getTimestamp("created_at");
                if (ts != null) {
                    quiz.setCreatedAt(ts.toLocalDateTime());
                }

                quizzes.add(quiz);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error fetching quizzes", e);
        }

        return quizzes;
    }

    @Override
    public List<Quiz> getOwnQuizzes(String playerId) {
        String sql = "SELECT * FROM quizzes WHERE player_id = ? ORDER BY created_at DESC";
        List<Quiz> quizzes = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Quiz quiz = new Quiz();
                    quiz.setId(rs.getString("id"));
                    quiz.setTitle(rs.getString("title"));
                    quiz.setQuestionNumber(rs.getInt("question_number"));
                    quiz.setPlayerId(rs.getString("player_id"));

                    String level = rs.getString("level");
                    if (level != null) quiz.setLevel(level);

                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) {
                        quiz.setCreatedAt(ts.toLocalDateTime());
                    }

                    quizzes.add(quiz);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching own quizzes for playerId=" + playerId, e);
        }

        return quizzes;
    }
}
