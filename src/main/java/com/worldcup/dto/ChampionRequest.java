package com.worldcup.dto;

/** Cialo zadania PUT /api/champion - kod ISO wybranej druzyny (lub null, by wyczyscic typ). */
public class ChampionRequest {

    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
