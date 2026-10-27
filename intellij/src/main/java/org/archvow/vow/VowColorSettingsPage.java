package org.archvow.vow;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import java.util.Map;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Settings > Editor > Color Scheme > Vow. */
public final class VowColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = {
        new AttributesDescriptor("First line (vow 1 component)", VowSyntaxHighlighter.HEADER),
        new AttributesDescriptor("Name on the first line", VowSyntaxHighlighter.NAME),
        new AttributesDescriptor("Keyword//Depth 0, not indented (name, type, uses, decision, hazard)", VowSyntaxHighlighter.KEY_0),
        new AttributesDescriptor("Keyword//Depth 1 (question, answer, by, on, kind, a column)", VowSyntaxHighlighter.KEY_1),
        new AttributesDescriptor("Keyword//Depth 2 (components of a rule's scope, answer of P1)", VowSyntaxHighlighter.KEY_2),
        new AttributesDescriptor("Keyword//Depth 3 and deeper", VowSyntaxHighlighter.KEY_3),
        new AttributesDescriptor("Text in quotes", VowSyntaxHighlighter.STRING),
        new AttributesDescriptor("Number", VowSyntaxHighlighter.NUMBER),
        new AttributesDescriptor("true, false, null", VowSyntaxHighlighter.LITERAL),
        new AttributesDescriptor("Id (H1, INV2, R1)", VowSyntaxHighlighter.ID),
        new AttributesDescriptor("Person or group (@alice, alice@acme.com)", VowSyntaxHighlighter.PERSON),
        new AttributesDescriptor("Date", VowSyntaxHighlighter.DATE),
        new AttributesDescriptor("Word", VowSyntaxHighlighter.WORD),
        new AttributesDescriptor("Brackets, {}", VowSyntaxHighlighter.BRACKETS),
        new AttributesDescriptor("Comma", VowSyntaxHighlighter.COMMA),
        new AttributesDescriptor("JSON (key = ...)", VowSyntaxHighlighter.JSON),
        new AttributesDescriptor("Comment", VowSyntaxHighlighter.COMMENT),
        new AttributesDescriptor("Unreadable", VowSyntaxHighlighter.BAD),
    };

    @Override
    public @Nullable Icon getIcon() {
        return VowIcons.FILE;
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return new VowSyntaxHighlighter();
    }

    @Override
    public @NotNull String getDemoText() {
        return """
                vow 1 component clean_orders
                # archvow vow of clean_orders - what it must be and do.
                name clean_orders
                type transformation
                layer p1_domain
                ticket US-002
                description "Keep only the valid rows"
                uses [orders_schema]

                # --- decisions
                decision 1
                  question "Which rows does it keep?"
                  answer "Rows where order_id is not null"
                  by @acme/architects
                  on 2026-10-21T10:00:00+00:00

                decision 2
                  question "Which columns hold personal data?"
                  answer "none"
                  by alice@acme.com

                columns
                  order_id none
                  email personal

                # --- hazards
                hazard H1
                  guide_word less
                  severity high
                  never "A valid row is dropped"

                invariant INV1
                  always "every valid row is in the output"
                  prevents [H1]
                  verified_by property_test
                rule R1
                  text "vat is rounded half-up"
                  scope
                    components [compute_vat]
                answers
                  P.retention
                    answer "Kept 30 days"
                    detail
                      by alice@acme.com
                odd = [[1, 2]]
                assumptions []
                """;
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Vow";
    }
}
