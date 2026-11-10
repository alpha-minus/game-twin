package com.twinlore.app.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A lightweight Markdown renderer for chat answers. Covers the cases models
 * actually emit — headers, bullet/numbered lists, fenced code blocks, and inline
 * **bold** / *italic* / `code`. Deliberately tolerant of partial/streaming input
 * (an unclosed marker just renders literally until its closer arrives).
 */
@Composable
fun MarkdownText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val codeBg = color.copy(alpha = 0.10f)
    val lines = markdown.split("\n")
    Column(modifier = modifier) {
        var i = 0
        var inFence = false
        val code = StringBuilder()
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimStart()
            if (line.startsWith("```")) {
                if (inFence) {
                    CodeBlock(code.toString().trimEnd('\n'), color, codeBg)
                    code.clear()
                    inFence = false
                } else {
                    inFence = true
                }
                i++
                continue
            }
            if (inFence) {
                code.append(raw).append('\n')
                i++
                continue
            }
            when {
                line.startsWith("### ") -> Header(line.removePrefix("### "), color, codeBg, 16.sp)
                line.startsWith("## ") -> Header(line.removePrefix("## "), color, codeBg, 18.sp)
                line.startsWith("# ") -> Header(line.removePrefix("# "), color, codeBg, 20.sp)
                line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") ->
                    Bullet("•", parseInline(line.drop(2), color, codeBg), color)
                NUMBERED.containsMatchIn(line) -> {
                    val marker = line.substringBefore(' ')
                    Bullet(marker, parseInline(line.substringAfter(' '), color, codeBg), color)
                }
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                else -> Text(
                    parseInline(raw, color, codeBg),
                    color = color,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            i++
        }
        if (inFence && code.isNotEmpty()) CodeBlock(code.toString().trimEnd('\n'), color, codeBg)
    }
}

private val NUMBERED = Regex("^\\d+\\. ")

@Composable
private fun Header(text: String, color: Color, codeBg: Color, size: androidx.compose.ui.unit.TextUnit) {
    Text(
        parseInline(text, color, codeBg),
        color = color,
        fontWeight = FontWeight.Bold,
        fontSize = size,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun Bullet(marker: String, content: AnnotatedString, color: Color) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            "$marker ",
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(content, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CodeBlock(text: String, color: Color, codeBg: Color) {
    Text(
        text = text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(codeBg)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
    )
}

/**
 * Parses inline bold, italic, and code spans into an AnnotatedString. Plain,
 * bold, and italic text also has LaTeX-style symbol commands mapped to Unicode
 * (see [substituteLatexSymbols]); inline `code` spans are left verbatim so a
 * literal `\rightarrow` in a code sample stays as typed.
 */
private fun parseInline(text: String, color: Color, codeBg: Color): AnnotatedString = buildAnnotatedString {
    val plain = StringBuilder()
    fun flushPlain() {
        if (plain.isNotEmpty()) {
            append(substituteLatexSymbols(plain.toString()))
            plain.clear()
        }
    }
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    flushPlain()
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(substituteLatexSymbols(text.substring(i + 2, end)))
                    }
                    i = end + 2
                } else {
                    plain.append(text[i]); i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    flushPlain()
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    plain.append(text[i]); i++
                }
            }
            (text[i] == '*' || text[i] == '_') -> {
                val marker = text[i]
                val end = text.indexOf(marker, i + 1)
                if (end != -1 && end > i + 1) {
                    flushPlain()
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(substituteLatexSymbols(text.substring(i + 1, end)))
                    }
                    i = end + 1
                } else {
                    plain.append(text[i]); i++
                }
            }
            else -> {
                plain.append(text[i]); i++
            }
        }
    }
    flushPlain()
}

/**
 * Maps LaTeX-style symbol commands models emit (e.g. `\rightarrow`, `\times`,
 * `\alpha`) to their Unicode glyph, and strips `$…$` / `$$…$$` math delimiters —
 * but only when the delimited span actually contains a command, so in-game
 * prices like `$5` are left alone. Unknown commands (`\foobar`) pass through
 * untouched. Single forward pass, no regex, no allocation on plain prose.
 */
internal fun substituteLatexSymbols(input: String): String {
    // Fast path: nothing to do without a backslash (commands) or dollar (math).
    if (input.indexOf('\\') == -1 && input.indexOf('$') == -1) return input
    val out = StringBuilder(input.length)
    var i = 0
    while (i < input.length) {
        val c = input[i]
        when {
            c == '\\' -> {
                var j = i + 1
                while (j < input.length && input[j].isLetter()) j++
                if (j > i + 1) {
                    val name = input.substring(i + 1, j)
                    val glyph = LATEX_SYMBOLS[name]
                    if (glyph != null) out.append(glyph) else out.append('\\').append(name)
                    i = j
                } else {
                    out.append('\\'); i++ // lone backslash
                }
            }
            c == '$' -> {
                val doubled = input.startsWith("$$", i)
                val delim = if (doubled) "$$" else "$"
                // A math span ends at the matching delimiter on the same line.
                val nl = input.indexOf('\n', i + delim.length)
                val close = input.indexOf(delim, i + delim.length)
                if (close != -1 && (nl == -1 || close < nl) &&
                    input.substring(i + delim.length, close).contains('\\')
                ) {
                    out.append(substituteLatexSymbols(input.substring(i + delim.length, close)))
                    i = close + delim.length
                } else {
                    out.append('$'); i++ // literal $ (price, or unpaired) — re-scan next char
                }
            }
            else -> {
                out.append(c); i++
            }
        }
    }
    return out.toString()
}

/** LaTeX command name (without the leading backslash) → Unicode glyph. */
private val LATEX_SYMBOLS: Map<String, String> = mapOf(
    // Arrows
    "rightarrow" to "→", "to" to "→", "Rightarrow" to "⇒", "implies" to "⇒",
    "leftarrow" to "←", "gets" to "←", "Leftarrow" to "⇐",
    "leftrightarrow" to "↔", "Leftrightarrow" to "⇔", "mapsto" to "↦",
    "uparrow" to "↑", "downarrow" to "↓",
    // Operators & relations
    "times" to "×", "div" to "÷", "cdot" to "·", "ast" to "∗", "star" to "⋆",
    "pm" to "±", "mp" to "∓", "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥",
    "neq" to "≠", "ne" to "≠", "approx" to "≈", "equiv" to "≡", "sim" to "∼",
    "propto" to "∝", "infty" to "∞", "sqrt" to "√", "sum" to "∑", "prod" to "∏",
    "int" to "∫", "partial" to "∂", "nabla" to "∇", "oplus" to "⊕", "otimes" to "⊗",
    "circ" to "∘", "bullet" to "•", "degree" to "°", "angle" to "∠", "perp" to "⊥",
    "parallel" to "∥", "prime" to "′",
    // Sets & logic
    "in" to "∈", "notin" to "∉", "subset" to "⊂", "subseteq" to "⊆",
    "supset" to "⊃", "supseteq" to "⊇", "cup" to "∪", "cap" to "∩",
    "emptyset" to "∅", "varnothing" to "∅", "forall" to "∀", "exists" to "∃",
    "neg" to "¬", "wedge" to "∧", "vee" to "∨", "land" to "∧", "lor" to "∨",
    // Dots
    "ldots" to "…", "dots" to "…", "cdots" to "⋯",
    // Greek lowercase
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
    "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
    "theta" to "θ", "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ",
    "nu" to "ν", "xi" to "ξ", "pi" to "π", "rho" to "ρ", "sigma" to "σ",
    "tau" to "τ", "upsilon" to "υ", "phi" to "φ", "varphi" to "φ", "chi" to "χ",
    "psi" to "ψ", "omega" to "ω",
    // Greek uppercase
    "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ",
    "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
)
