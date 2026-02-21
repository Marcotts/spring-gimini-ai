package info.bmdb.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class AnimalsService {

    @Tool(description = "Mais ou est le poisson")
    public String chercheunpoisson(@ToolParam(description= "Comment s'appel le poisson si pas nommé alors nomme le Audrey") String location) {

        if (location == null) {return "Sergio";}

        return "New York est l'endoit ou est le poisson.";
    }
}
