package com.twinlore.app.ui.screens.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class SubstituteLatexSymbolsTest {

    @Test
    fun maps_known_arrow_command() {
        assertEquals("go left → right", substituteLatexSymbols("go left \\rightarrow right"))
    }

    @Test
    fun maps_several_known_commands() {
        assertEquals("x × 2 ≤ 5, α", substituteLatexSymbols("x \\times 2 \\leq 5, \\alpha"))
    }

    @Test
    fun unknown_command_stays_untouched() {
        assertEquals("use \\foobar here", substituteLatexSymbols("use \\foobar here"))
    }

    @Test
    fun lone_backslash_stays() {
        assertEquals("a\\b path", substituteLatexSymbols("a\\b path"))
    }

    @Test
    fun command_terminated_by_letters_is_not_matched() {
        // \rightarrowx is not \rightarrow followed by x — the run is one unknown token.
        assertEquals("\\rightarrowx", substituteLatexSymbols("\\rightarrowx"))
    }

    @Test
    fun strips_math_delimiters_around_a_command() {
        assertEquals("then → now", substituteLatexSymbols("then \$\\rightarrow\$ now"))
    }

    @Test
    fun strips_double_dollar_display_math_with_command() {
        assertEquals("α", substituteLatexSymbols("\$\$\\alpha\$\$"))
    }

    @Test
    fun leaves_currency_price_untouched() {
        assertEquals("it costs \$5 here", substituteLatexSymbols("it costs \$5 here"))
    }

    @Test
    fun leaves_two_prices_untouched() {
        assertEquals("\$5 or \$10", substituteLatexSymbols("\$5 or \$10"))
    }

    @Test
    fun leaves_dollar_span_without_command_untouched() {
        // Paired $...$ but no backslash inside → not math, keep literal.
        assertEquals("pay \$5 now\$ done", substituteLatexSymbols("pay \$5 now\$ done"))
    }

    @Test
    fun does_not_cross_newline_for_math_span() {
        // The $…$ delimiters straddle a newline, so they are NOT treated as a
        // math span and stay literal — but a bare command still maps anywhere.
        assertEquals("\$a\n×\$", substituteLatexSymbols("\$a\n\\times\$"))
    }

    @Test
    fun empty_string() {
        assertEquals("", substituteLatexSymbols(""))
    }

    @Test
    fun plain_text_unchanged() {
        assertEquals("just normal prose.", substituteLatexSymbols("just normal prose."))
    }

    @Test
    fun trailing_backslash_at_end() {
        assertEquals("end\\", substituteLatexSymbols("end\\"))
    }
}
