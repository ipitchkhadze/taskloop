package com.example.tasklist.lmstudio;

import java.util.List;

/**
 * Board title + ancestor task titles (root → immediate parent) + current task for LM Studio.
 * Prompts instruct the model to use board/ancestors only as scene-setting; advice must target {@code taskTitle} alone.
 */
public record AdvicePromptContext(String boardTitle, List<String> ancestorTitlesRootFirst, String taskTitle) {
}
