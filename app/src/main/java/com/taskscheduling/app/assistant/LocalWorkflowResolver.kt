package com.taskscheduling.app.assistant

import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.Workflow
import java.util.Locale

/**
 * Local implementation of [WorkflowResolver].
 * Searches persisted workflows for the requested intent or user command text.
 * Strictly searches existing workflows and never creates new ones automatically.
 * 100% offline, deterministic local matching without cloud AI.
 */
class LocalWorkflowResolver(
    private val persistenceRepository: WorkflowPersistenceRepository
) : WorkflowResolver {

    override fun getWallpaperWorkflows(): List<Workflow> {
        val allWorkflows = persistenceRepository.getAllWorkflows()
        return allWorkflows.filter { wf ->
            (wf.name.contains("wallpaper", ignoreCase = true) || wf.id == "wf_wallpaper") &&
            wf.steps.isNotEmpty()
        }
    }

    override fun resolveWorkflow(intent: CommandIntent): Workflow? {
        return when (intent) {
            CommandIntent.CHANGE_WALLPAPER -> {
                val wallpapers = getWallpaperWorkflows()
                if (wallpapers.size == 1) wallpapers.first() else null
            }
            else -> null
        }
    }

    override fun resolveByName(commandText: String): WorkflowResolution {
        val trimmed = commandText.trim()
        if (trimmed.isBlank()) {
            return WorkflowResolution.NotFound("")
        }

        val allWorkflows = persistenceRepository.getAllWorkflows()
        val extracted = extractCandidateQueries(trimmed)
        val displayQuery = extracted.displayQuery

        if (allWorkflows.isEmpty()) {
            return WorkflowResolution.NotFound(displayQuery)
        }

        // STAGE 1: Exact Workflow Name Match (case-insensitive)
        for (candidate in extracted.candidates) {
            val exactMatches = allWorkflows.filter { wf ->
                wf.name.trim().equals(candidate.trim(), ignoreCase = true)
            }
            if (exactMatches.size == 1) {
                return WorkflowResolution.Found(exactMatches.first())
            } else if (exactMatches.size > 1) {
                return WorkflowResolution.Ambiguous(displayQuery, exactMatches)
            }
        }

        // STAGE 2: Normalized Exact Match
        for (candidate in extracted.candidates) {
            val normCandidate = normalize(candidate)
            if (normCandidate.isBlank()) continue

            val normMatches = allWorkflows.filter { wf ->
                normalize(wf.name) == normCandidate
            }
            if (normMatches.size == 1) {
                return WorkflowResolution.Found(normMatches.first())
            } else if (normMatches.size > 1) {
                return WorkflowResolution.Ambiguous(displayQuery, normMatches)
            }
        }

        // STAGE 3: Safe Phrase / Token Match
        for (candidate in extracted.candidates) {
            val normCandidate = normalize(candidate)
            if (normCandidate.isBlank() || normCandidate.length < 2) continue

            val phraseMatches = allWorkflows.filter { wf ->
                val normWfName = normalize(wf.name)
                normWfName.isWordMatch(normCandidate) || normCandidate.isWordMatch(normWfName)
            }.distinctBy { it.id }

            if (phraseMatches.size == 1) {
                return WorkflowResolution.Found(phraseMatches.first())
            } else if (phraseMatches.size > 1) {
                return WorkflowResolution.Ambiguous(displayQuery, phraseMatches)
            }
        }

        // STAGE 4: Not Found
        return WorkflowResolution.NotFound(displayQuery)
    }

    private data class ExtractedQuery(
        val candidates: List<String>,
        val displayQuery: String
    )

    private fun extractCandidateQueries(text: String): ExtractedQuery {
        val normalized = normalize(text)
        val words = normalized.split(" ").filter { it.isNotBlank() }

        // Remove command prefixes (e.g., "run", "start", "execute", "do", "launch", "play")
        val commandVerbs = setOf("run", "start", "execute", "do", "launch", "play")
        val articles = setOf("my", "the", "a", "an", "our")
        val politePrefixes = setOf("please", "can", "you", "i", "want", "to", "could", "would", "hey", "assistant")

        var startIndex = 0
        while (startIndex < words.size && words[startIndex] in politePrefixes) {
            startIndex++
        }
        if (startIndex < words.size && words[startIndex] in commandVerbs) {
            startIndex++
        }
        while (startIndex < words.size && words[startIndex] in articles) {
            startIndex++
        }

        val strippedWords = words.subList(startIndex.coerceAtMost(words.size), words.size)
        val strippedQuery = strippedWords.joinToString(" ")

        // Also check if there is a trailing workflow suffix (e.g., "workflow", "routine", "task", "flow")
        val suffixes = setOf("workflow", "routine", "task", "flow")
        val suffixStrippedWords = if (strippedWords.size > 1 && strippedWords.last() in suffixes) {
            strippedWords.dropLast(1)
        } else {
            strippedWords
        }
        val suffixStrippedQuery = suffixStrippedWords.joinToString(" ")

        // Generate candidate list in order of priority:
        val candidates = mutableListOf<String>()
        if (strippedQuery.isNotBlank()) candidates.add(strippedQuery)
        if (suffixStrippedQuery.isNotBlank() && suffixStrippedQuery != strippedQuery) {
            candidates.add(suffixStrippedQuery)
        }
        if (normalized.isNotBlank() && !candidates.contains(normalized)) {
            candidates.add(normalized)
        }

        // Format user-friendly display query (Title Case)
        val displayWords = if (strippedWords.isNotEmpty()) strippedWords else words
        val displayQuery = displayWords.joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

        return ExtractedQuery(
            candidates = candidates,
            displayQuery = displayQuery.ifBlank { text.trim() }
        )
    }

    private fun normalize(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.isWordMatch(target: String): Boolean {
        if (this == target) return true
        val thisWords = this.split(" ")
        val targetWords = target.split(" ")
        if (targetWords.isEmpty() || thisWords.isEmpty()) return false
        if (thisWords.size > targetWords.size && thisWords.take(targetWords.size) == targetWords) return true
        val pattern = "\\b" + Regex.escape(target) + "\\b"
        return Regex(pattern).containsMatchIn(this)
    }
}
