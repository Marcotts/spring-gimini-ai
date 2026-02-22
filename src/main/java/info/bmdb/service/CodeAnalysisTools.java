package info.bmdb.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Outils d'analyse et de génération de code pour Spring AI
 */
public class CodeAnalysisTools {

    @Tool(description = "Analyse un fichier de code Java et extrait sa structure (classes, méthodes, imports)")
    public String analyzeJavaFile(
            @ToolParam(description = "Chemin vers le fichier Java à analyser") String filePath
    ) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path) || !path.toString().endsWith(".java")) {
                return "[ERROR] Fichier Java non trouvé ou invalide: " + filePath;
            }

            String content = Files.readString(path);
            StringBuilder analysis = new StringBuilder();
            
            analysis.append("=== ANALYSE DE: ").append(filePath).append(" ===\n\n");
            
            // Extraire le package
            Pattern packagePattern = Pattern.compile("package\\s+([\\w\\.]+);");
            Matcher packageMatcher = packagePattern.matcher(content);
            if (packageMatcher.find()) {
                analysis.append("📦 Package: ").append(packageMatcher.group(1)).append("\n");
            }
            
            // Extraire les imports
            Pattern importPattern = Pattern.compile("import\\s+([\\w\\.\\*]+);");
            List<String> imports = importPattern.matcher(content)
                    .results()
                    .map(m -> m.group(1))
                    .collect(Collectors.toList());
            
            if (!imports.isEmpty()) {
                analysis.append("\n📚 Imports (").append(imports.size()).append("):\n");
                imports.forEach(imp -> analysis.append("  - ").append(imp).append("\n"));
            }
            
            // Extraire les classes/interfaces
            Pattern classPattern = Pattern.compile("(?:public|private|protected)?\\s*(?:abstract)?\\s*(?:class|interface|enum)\\s+(\\w+)");
            List<String> classes = classPattern.matcher(content)
                    .results()
                    .map(m -> m.group(1))
                    .collect(Collectors.toList());
            
            if (!classes.isEmpty()) {
                analysis.append("\n🏗️ Classes/Interfaces:\n");
                classes.forEach(cls -> analysis.append("  - ").append(cls).append("\n"));
            }
            
            // Extraire les méthodes publiques
            Pattern methodPattern = Pattern.compile("public\\s+(?:static\\s+)?(?:\\w+(?:<[^>]+>)?\\s+)?([\\w]+)\\s*\\([^)]*\\)");
            List<String> methods = methodPattern.matcher(content)
                    .results()
                    .map(m -> m.group(1))
                    .distinct()
                    .collect(Collectors.toList());
            
            if (!methods.isEmpty()) {
                analysis.append("\n⚙️ Méthodes publiques (").append(methods.size()).append("):\n");
                methods.forEach(method -> analysis.append("  - ").append(method).append("()\n"));
            }
            
            // Détecter les annotations Spring AI
            if (content.contains("@Tool")) {
                long toolCount = content.lines()
                        .filter(line -> line.trim().startsWith("@Tool"))
                        .count();
                analysis.append("\n🔧 Outils Spring AI détectés: ").append(toolCount).append("\n");
            }
            
            return analysis.toString();
            
        } catch (IOException e) {
            return "[ERROR] Impossible de lire le fichier: " + e.getMessage();
        }
    }
    
    @Tool(description = "Génère un template de classe Spring AI Tool basé sur une description")
    public String generateSpringAiTool(
            @ToolParam(description = "Nom de la classe à générer") String className,
            @ToolParam(description = "Description de la fonctionnalité de l'outil") String toolDescription,
            @ToolParam(description = "Package où placer la classe") String packageName
    ) {
        StringBuilder template = new StringBuilder();
        
        template.append("package ").append(packageName).append(";\n\n");
        template.append("import org.springframework.ai.tool.annotation.Tool;\n");
        template.append("import org.springframework.ai.tool.annotation.ToolParam;\n\n");
        template.append("/**\n");
        template.append(" * ").append(toolDescription).append("\n");
        template.append(" */\n");
        template.append("public class ").append(className).append(" {\n\n");
        template.append("    @Tool(description = \"").append(toolDescription).append("\")\n");
        template.append("    public String execute(\n");
        template.append("            @ToolParam(description = \"Paramètre d'entrée\") String input\n");
        template.append("    ) {\n");
        template.append("        // TODO: Implémenter la logique métier\n");
        template.append("        try {\n");
        template.append("            // Votre code ici\n");
        template.append("            return \"Résultat pour: \" + input;\n");
        template.append("        } catch (Exception e) {\n");
        template.append("            return \"[ERROR] \" + e.getMessage();\n");
        template.append("        }\n");
        template.append("    }\n");
        template.append("}\n");
        
        return template.toString();
    }
}
