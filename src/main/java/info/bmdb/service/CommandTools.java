package info.bmdb.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * FR: Outils pour exécuter des commandes locales et explorer le système de fichiers.
 * EN: Tools to run local commands and browse the local filesystem.
 */
public class CommandTools {

    private static final int DEFAULT_TIMEOUT_SEC = 30;

    @Tool(description = "FR: Donne des infos système (OS, arch, répertoire courant) et vérifie Python. EN: System info (OS, arch, cwd) and Python availability.")
    public String systemInfo() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String osArch = System.getProperty("os.arch", "");
        String userDir = System.getProperty("user.dir", "");
        String shell = isWindows() ? "cmd.exe" : "/bin/sh";
        String python = detectPython();
        return new StringBuilder()
                .append("OS: ").append(osName).append(" (arch ").append(osArch).append(")\n")
                .append("Shell: ").append(shell).append("\n")
                .append("Current directory: ").append(userDir).append("\n")
                .append("Python: ").append(python)
                .toString();
    }

    @Tool(description = "FR: Exécute une commande dans le shell local. EN: Execute a local shell command.")
    public String exec(
            @ToolParam(description = "FR: La commande à exécuter. EN: Command to run.") String command,
            @ToolParam(description = "FR: Répertoire de travail (optionnel). EN: Working directory (optional).") String workingDir,
            @ToolParam(description = "FR: Délai max en secondes (optionnel, défaut 30s). EN: Timeout seconds (optional, default 30s).") Integer timeoutSeconds
    ) {
        if (command == null || command.isBlank()) {
            return "[ERROR] Command is empty";
        }
        int timeout = (timeoutSeconds == null || timeoutSeconds <= 0) ? DEFAULT_TIMEOUT_SEC : timeoutSeconds;
        List<String> fullCmd = new ArrayList<>();
        if (isWindows()) {
            fullCmd.add("cmd.exe");
            fullCmd.add("/c");
            fullCmd.add(command);
        } else {
            fullCmd.add("/bin/sh");
            fullCmd.add("-c");
            fullCmd.add(command);
        }

        ProcessBuilder pb = new ProcessBuilder(fullCmd);
        if (workingDir != null && !workingDir.isBlank()) {
            pb.directory(new File(workingDir));
        }
        // Inherit system environment; merge error stream to capture both
        pb.redirectErrorStream(true);
        Charset cs = defaultCharset();

        try {
            Process p = pb.start();
            boolean finished = p.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return "[TIMEOUT] Process exceeded " + timeout + " seconds";
            }
            String output;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), cs))) {
                output = br.lines().collect(Collectors.joining(System.lineSeparator()));
            }
            int code = p.exitValue();
            return "[EXIT " + code + "]\n" + output;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[ERROR] " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    @Tool(description = "FR: Liste les fichiers d'un répertoire (récursif, limité). EN: List files in a directory (recursive, limited).")
    public String list(
            @ToolParam(description = "FR: Chemin de départ (optionnel, défaut=répertoire courant). EN: Start path (optional, default=current dir).") String path,
            @ToolParam(description = "FR: Profondeur max (optionnel, défaut=2). EN: Max depth (optional, default=2).") Integer maxDepth,
            @ToolParam(description = "FR: Nombre maximum d'entrées (optionnel, défaut=200). EN: Max entries (optional, default=200).") Integer maxEntries
    ) {
        int depth = (maxDepth == null || maxDepth < 0) ? 2 : maxDepth;
        int limit = (maxEntries == null || maxEntries <= 0) ? 200 : maxEntries;
        Path start = (path == null || path.isBlank()) ? Paths.get(System.getProperty("user.dir", ".")) : Paths.get(path);
        if (!Files.exists(start)) {
            return "[ERROR] Path does not exist: " + start.toAbsolutePath();
        }
        if (!Files.isDirectory(start)) {
            return "[ERROR] Not a directory: " + start.toAbsolutePath();
        }
        try (Stream<Path> s = Files.walk(start, Math.max(1, depth))) {
            List<Path> entries = s
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(limit)
                    .collect(Collectors.toList());
            String base = start.toAbsolutePath().normalize().toString();
            StringBuilder sb = new StringBuilder();
            sb.append("Base: ").append(base)
              .append("\nDepth<=").append(depth)
              .append(", MaxEntries=").append(limit)
              .append("\n");
            for (Path p : entries) {
                String rel;
                try {
                    rel = start.relativize(p).toString();
                } catch (Exception ex) {
                    rel = p.toAbsolutePath().normalize().toString();
                }
                if (rel.isEmpty()) rel = ".";
                sb.append(Files.isDirectory(p) ? "[D] " : "[F] ")
                  .append(rel)
                  .append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return "[ERROR] " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    private static String detectPython() {
        // Try common commands: on Windows, also try 'py -V'
        String[] candidates = isWindows()
                ? new String[]{"python --version", "py --version", "python3 --version"}
                : new String[]{"python3 --version", "python --version"};
        for (String cmd : candidates) {
            String res = runQuick(cmd);
            if (res != null && !res.startsWith("[ERROR]") && !res.startsWith("[TIMEOUT]")) {
                // Norm the first line only
                String firstLine = res.lines().findFirst().orElse(res).trim();
                if (!firstLine.isEmpty()) {
                    return firstLine + " (via '" + cmd + "')";
                }
            }
        }
        return "Not found";
    }

    private static String runQuick(String command) {
        List<String> full = new ArrayList<>();
        if (isWindows()) {
            full.add("cmd.exe"); full.add("/c"); full.add(command);
        } else {
            full.add("/bin/sh"); full.add("-c"); full.add(command);
        }
        try {
            Process p = new ProcessBuilder(full).redirectErrorStream(true).start();
            boolean ok = p.waitFor(5, TimeUnit.SECONDS);
            if (!ok) {
                p.destroyForcibly();
                return "[TIMEOUT]";
            }
            Charset cs = defaultCharset();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), cs))) {
                return br.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            return "[ERROR] " + e.getMessage();
        }
    }

    private static Charset defaultCharset() {
        // Try to align with system default; fallback to UTF-8
        try {
            return Charset.defaultCharset();
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }
}
