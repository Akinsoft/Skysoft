package com.skysoft.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression

class SkysoftRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("skysoft")

    override fun instance(): RuleSet =
        RuleSet(
            ruleSetId,
            mapOf(
                RuleName("AmbiguousBooleanReturn") to ::AmbiguousBooleanReturn,
            ),
        )
}

class AmbiguousBooleanReturn(config: Config) : Rule(
    config,
    "Boolean results should read like predicates or name the action outcome; use a named enum or result type otherwise.",
) {
    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        val name = function.name ?: return
        if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return
        if (!function.hasBooleanReturn()) return
        if (name.isPredicateName()) return

        report(
            Finding(
                Entity.atName(function),
                "Boolean-returning function '$name' should read like a predicate, name the action outcome, or return a named result type.",
            ),
        )
    }

    override fun visitParameter(parameter: KtParameter) {
        super.visitParameter(parameter)
        val name = parameter.name ?: return
        if (!parameter.hasBooleanReturningFunctionType()) return
        if (name.isPredicateName()) return

        report(
            Finding(
                Entity.atName(parameter),
                "Boolean-returning callback parameter '$name' is ambiguous; use a predicate/outcome name or named result type.",
            ),
        )
    }

    private fun KtNamedFunction.hasBooleanReturn(): Boolean =
        hasExplicitBooleanReturn() || hasInferredBooleanExpressionReturn()

    private fun KtNamedFunction.hasExplicitBooleanReturn(): Boolean =
        typeReference?.text?.isBooleanType() == true

    private fun KtNamedFunction.hasInferredBooleanExpressionReturn(): Boolean =
        typeReference == null && !hasBlockBody() && bodyExpression?.looksLikeBooleanExpression() == true

    private fun KtExpression.looksLikeBooleanExpression(): Boolean =
        when (this) {
            is KtConstantExpression -> text == TRUE_LITERAL || text == FALSE_LITERAL
            is KtIsExpression -> true
            is KtBinaryExpression -> operationToken in BOOLEAN_OPERATION_TOKENS
            is KtPrefixExpression -> operationToken == KtTokens.EXCL && baseExpression?.looksLikeBooleanExpression() == true
            is KtParenthesizedExpression -> expression?.looksLikeBooleanExpression() == true
            else -> false
        }

    private fun KtParameter.hasBooleanReturningFunctionType(): Boolean {
        val text = typeReference?.text ?: return false
        return FUNCTION_ARROW in text && text.substringAfterLast(FUNCTION_ARROW).trim().isBooleanType()
    }

    private fun String.isBooleanType(): Boolean =
        this == BOOLEAN_TYPE || this == KOTLIN_BOOLEAN_TYPE

    private fun String.isPredicateName(): Boolean =
        PREDICATE_PREFIXES.any { prefix -> startsWith(prefix) } ||
            ACTION_OUTCOME_PATTERNS.any { pattern -> pattern.matches(this) } ||
            PREDICATE_PARTS.any { part -> contains(part) } ||
            PREDICATE_SUFFIXES.any { suffix -> endsWith(suffix) } ||
            this in ALLOWED_BOOLEAN_NAMES

    private companion object {
        const val BOOLEAN_TYPE = "Boolean"
        const val KOTLIN_BOOLEAN_TYPE = "kotlin.Boolean"
        const val FUNCTION_ARROW = "->"
        const val TRUE_LITERAL = "true"
        const val FALSE_LITERAL = "false"

        val BOOLEAN_OPERATION_TOKENS = setOf(
            KtTokens.ANDAND,
            KtTokens.OROR,
            KtTokens.EQEQ,
            KtTokens.EXCLEQ,
            KtTokens.EQEQEQ,
            KtTokens.EXCLEQEQEQ,
            KtTokens.IN_KEYWORD,
            KtTokens.NOT_IN,
            KtTokens.LT,
            KtTokens.GT,
            KtTokens.LTEQ,
            KtTokens.GTEQ,
        )

        val PREDICATE_PREFIXES = listOf(
            "accepts",
            "allows",
            "are",
            "can",
            "closeTo",
            "contains",
            "did",
            "does",
            "endsWith",
            "equals",
            "fits",
            "has",
            "have",
            "holding",
            "includes",
            "intersects",
            "involves",
            "is",
            "looksLike",
            "matches",
            "needs",
            "overlaps",
            "outside",
            "pointIn",
            "requires",
            "same",
            "should",
            "startsWith",
            "supports",
            "uses",
            "was",
            "were",
            "will",
        )

        val ACTION_OUTCOME_PATTERNS = listOf(
            Regex("""try(Handle|Navigate|Send|Swap)[A-Z].*"""),
            Regex("""consumeRecent[A-Z].*"""),
        )

        val PREDICATE_SUFFIXES = listOf(
            "Active",
            "Allowed",
            "Available",
            "Close",
            "Contains",
            "Enabled",
            "Exists",
            "Inside",
            "Intersects",
            "Loaded",
            "Match",
            "Matches",
            "Near",
            "Open",
            "Plausible",
            "Present",
            "Ready",
            "Resolved",
            "Valid",
            "Visible",
        )

        val PREDICATE_PARTS = listOf(
            "ChangedOrReduced",
            "Contains",
            "Exists",
            "Has",
            "Intersects",
            "Matches",
            "Represents",
        )

        val ALLOWED_BOOLEAN_NAMES = setOf(
            "condition",
            "equals",
            "filter",
            "predicate",
            "visible",
        )
    }
}
