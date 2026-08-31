package dk.school.workoverviewagent.notepad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class NotepadService {

    public NotepadLaunchResult openWithMessage(String message) {
        requireWindows();

        String content = Objects.requireNonNull(message, "message must not be null");

        try {
            Path file = Files.createTempFile("work-overview-agent-notepad-", ".txt");
            Files.writeString(file, content, StandardCharsets.UTF_8);

            new ProcessBuilder("notepad.exe", file.toAbsolutePath().toString()).start();
            return new NotepadLaunchResult(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to open Notepad with the provided message", exception);
        }
    }

    private static void requireWindows() {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase();
        if (!operatingSystem.contains("win")) {
            throw new UnsupportedOperationException("Notepad can only be opened on Windows");
        }
    }
}
