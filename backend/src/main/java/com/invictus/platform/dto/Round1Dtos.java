package com.invictus.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** PLAYER-facing Round 1 DTOs. None of them has a field that can carry an answer list or the solution grid. */
public final class Round1Dtos {
    private Round1Dtos() {}

    /** status: NOT_PUBLISHED | NOT_STARTED | IN_PROGRESS | COMPLETED | CLOSED */
    public record Round1Summary(String status, int completed, int total, int correct, int incorrect,
                                int timeouts, int score) {}

    /** letter is only filled for words this team has already solved. */
    public record CellView(boolean open, Integer number, String letter) {}

    public record CrosswordView(int rows, int cols, List<CellView> cells) {}

    /** The current clue. row/col/direction/length let the UI highlight the word; none of it reveals letters. */
    public record QuestionView(Long attemptId, int displayNumber, String clue, String direction, int length,
                               int row, int col, long remainingMs, long totalMs) {}

    public record Round1State(Round1Summary summary, QuestionView question, CrosswordView crossword, String lastResult) {}

    public record AnswerRequest(@NotNull(message = "attemptId is required") Long attemptId,
                                @NotBlank(message = "Answer is required") @Size(max = 100) String answer) {}
}
