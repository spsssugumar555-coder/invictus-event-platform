package com.invictus.platform.service;

import com.invictus.platform.dto.Round1Dtos.CellView;
import com.invictus.platform.dto.Round1Dtos.CrosswordView;
import com.invictus.platform.entity.Question;
import com.invictus.platform.entity.Round1Config;
import com.invictus.platform.repository.QuestionRepository;
import com.invictus.platform.repository.Round1ConfigRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the crossword grid shown to people from the layout persisted on the questions. */
@Service
public class CrosswordService {
    private final QuestionRepository questions;
    private final Round1ConfigRepository config;

    public CrosswordService(QuestionRepository questions, Round1ConfigRepository config) {
        this.questions = questions;
        this.config = config;
    }

    /**
     * @param revealed   question ids whose letters may be shown (a team's own solved questions)
     * @param revealAll  true for the admin view
     * @return the grid, or null when no layout has been generated yet
     */
    @Transactional(readOnly = true)
    public CrosswordView view(Set<Long> revealed, boolean revealAll) {
        Round1Config c = config.findById(1L).orElse(null);
        if (c == null || c.gridRows <= 0 || c.gridCols <= 0) {
            return null;
        }
        int rows = c.gridRows;
        int cols = c.gridCols;
        boolean[] open = new boolean[rows * cols];
        Integer[] number = new Integer[rows * cols];
        String[] letter = new String[rows * cols];

        for (Question q : questions.findAllByOrderByNumberAsc()) {
            if (q.gridRow == null || q.gridCol == null) {
                continue;
            }
            String ans = CrosswordGenerator.normalize(q.answer);
            boolean across = "ACROSS".equals(q.direction);
            boolean show = revealAll || revealed.contains(q.id);
            for (int i = 0; i < ans.length(); i++) {
                int r = q.gridRow + (across ? 0 : i);
                int col = q.gridCol + (across ? i : 0);
                if (r < 0 || r >= rows || col < 0 || col >= cols) {
                    continue;
                }
                int idx = r * cols + col;
                open[idx] = true;
                if (i == 0 && q.displayNumber != null) {
                    number[idx] = q.displayNumber;
                }
                if (show) {
                    letter[idx] = String.valueOf(ans.charAt(i));
                }
            }
        }
        List<CellView> cells = new ArrayList<>(rows * cols);
        for (int i = 0; i < rows * cols; i++) {
            cells.add(new CellView(open[i], number[i], letter[i]));
        }
        return new CrosswordView(rows, cols, cells);
    }
}
