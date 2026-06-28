package com.worldcup.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultFetchServiceTest {

    private final ResultFetchService service = new ResultFetchService(null, null, null, null, null, null);

    @Test
    void isFinished_NormalizesAndMatchesCorrectly() {
        assertTrue(service.isFinished("FT"));
        assertTrue(service.isFinished("ft"));
        assertTrue(service.isFinished("AET"));
        assertTrue(service.isFinished("AP"));
        assertTrue(service.isFinished("Finished"));
        assertTrue(service.isFinished("Match Finished"));
        assertTrue(service.isFinished("Game Finished"));
        assertTrue(service.isFinished("PEN"));
        assertTrue(service.isFinished("pen."));
        assertTrue(service.isFinished("  match_finished  "));
        assertTrue(service.isFinished("Game-Finished"));

        assertFalse(service.isFinished("HT"));
        assertFalse(service.isFinished("1H"));
        assertFalse(service.isFinished("2H"));
        assertFalse(service.isFinished("Live"));
        assertFalse(service.isFinished("Not Started"));
        assertFalse(service.isFinished(null));
        assertFalse(service.isFinished(""));
        assertFalse(service.isFinished("   "));
    }
}
