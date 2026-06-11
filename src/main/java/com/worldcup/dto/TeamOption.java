package com.worldcup.dto;

/** Pojedyncza opcja na liscie druzyn do wyboru (typ na mistrza turnieju). */
public class TeamOption {

    private final String code;
    private final String name;

    public TeamOption(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
