package dk.school.workoverviewagent.notepad;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class NotepadTool {

    private final NotepadService notepadService;

    public NotepadTool(NotepadService notepadService) {
        this.notepadService = notepadService;
    }

    @McpTool(
        name = "open_notepad_with_message",
        description = "Open Notepad and write a message into a temporary text file")
    public String openNotepadWithMessage(
            @McpToolParam(description = "Message to write into Notepad", required = true) String message) {
        NotepadLaunchResult result = notepadService.openWithMessage(message);
        return "Opened Notepad with message in " + result.filePath();
    }
}
