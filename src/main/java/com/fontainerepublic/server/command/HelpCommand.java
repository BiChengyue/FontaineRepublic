package com.fontainerepublic.server.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Player-facing help guide (FR-CMD-GUIDE-001): replaces the old node-enumerating
 * {@code /fr help} with a bounded, per-module guide.
 *
 * <ul>
 *   <li>{@code /fr help} prints the fixed module index (all guide pages).</li>
 *   <li>{@code /fr help <module>} prints one bounded page per known module
 *       (money / bank / citizen / government / parliament / court); the
 *       operator-only institution page is restricted and never enumerates its
 *       commands.</li>
 *   <li>Unknown modules receive one bounded standard rejection.</li>
 * </ul>
 *
 * All text goes through {@link Component#translatableWithFallback(String, String,
 * Object...)} so clients with the Mod localize via the bundled
 * {@code zh_cn.json}/{@code en_us.json} language files while clients without the
 * Mod still receive the bounded English fallback. The guide is static and bounded:
 * fixed entries per module, no runtime queries, no hidden-command enumeration.
 */
public final class HelpCommand {
    private static final String KEY_PREFIX = "fontainerepublic.help.";

    /** Fixed guide order: player modules first, operator-only module last. */
    private static final List<String> KNOWN_MODULES = List.of(
            "money",
            "bank",
            "citizen",
            "government",
            "parliament",
            "court",
            "institution"
    );

    /** Bounded page entries per player module (module -> title + entries). */
    private static final Page MONEY_PAGE = new Page(
            "money.title",
            "money: personal money commands.",
            List.of(
                    new Entry(
                            "money.balance",
                            "  /fr money balance - view your balance"
                    ),
                    new Entry(
                            "money.pay",
                            "  /fr money pay <target> <amount> [memo] - pay another player "
                                    + "(name, UUID, or registry number)"
                    ),
                    new Entry(
                            "money.history",
                            "  /fr money history [page] - view your transaction history"
                    )
            )
    );

    private static final Page BANK_PAGE = new Page(
            "bank.title",
            "bank: central-bank official duties "
                    + "(on-site duty at a central-bank zone).",
            List.of(
                    new Entry(
                            "bank.balance",
                            "  /fr bank balance - view the national treasury total"
                    ),
                    new Entry(
                            "bank.deposit",
                            "  /fr bank deposit <target> <amount> <zoneId> [reason] "
                                    + "- official issuance"
                    ),
                    new Entry(
                            "bank.withdraw",
                            "  /fr bank withdraw <target> <amount> <zoneId> [reason] "
                                    + "- official withdrawal"
                    ),
                    new Entry(
                            "bank.freeze",
                            "  /fr bank freeze <target> <zoneId> [reason] - freeze an account"
                    ),
                    new Entry(
                            "bank.unfreeze",
                            "  /fr bank unfreeze <target> <zoneId> [reason] - unfreeze an account"
                    )
            )
    );

    private static final Page CITIZEN_PAGE = new Page(
            "citizen.title",
            "citizen: your citizen identity.",
            List.of(
                    new Entry(
                            "citizen.info",
                            "  /fr citizen info - view your citizen status and rank"
                    )
            )
    );

    private static final Page GOVERNMENT_PAGE = new Page(
            "government.title",
            "government: ministries, positions and appointments "
                    + "(on-site duty at a government zone).",
            List.of(
                    new Entry(
                            "government.ministry",
                            "  /fr government ministry create <name> | list - manage ministries"
                    ),
                    new Entry(
                            "government.position",
                            "  /fr government position create <ministryId> <title> "
                                    + "| list <ministryId> - manage positions"
                    ),
                    new Entry(
                            "government.appoint",
                            "  /fr government appoint <positionId> <holderUuid> <zoneId> "
                                    + "- appoint a holder"
                    ),
                    new Entry(
                            "government.dismiss",
                            "  /fr government dismiss <positionId> <reason> <zoneId> "
                                    + "- dismiss a holder"
                    ),
                    new Entry(
                            "government.office",
                            "  /fr government office <positionId> - view an office"
                    )
            )
    );

    private static final Page PARLIAMENT_PAGE = new Page(
            "parliament.title",
            "parliament: proposals, votes and bills "
                    + "(on-site duty at a parliament zone).",
            List.of(
                    new Entry(
                            "parliament.proposal",
                            "  /fr parliament proposal submit <title> <normLevel> <zoneId> "
                                    + "<fullText> | list [afterSeq] [limit] - proposals"
                    ),
                    new Entry(
                            "parliament.vote",
                            "  /fr parliament vote open <proposalId> <zoneId> "
                                    + "| cast <voteId> <choice> <zoneId> "
                                    + "| close <voteId> <zoneId> - votes"
                    ),
                    new Entry(
                            "parliament.bill",
                            "  /fr parliament bill show <billId> - view a bill"
                    )
            )
    );

    private static final Page COURT_PAGE = new Page(
            "court.title",
            "court: cases, evidence and verdicts "
                    + "(on-site duty at a court zone).",
            List.of(
                    new Entry(
                            "court.case",
                            "  /fr court case file <caseType> <title> <zoneId> "
                                    + "<description> | list [afterSeq] [limit] "
                                    + "| show <caseId> - cases"
                    ),
                    new Entry(
                            "court.evidence",
                            "  /fr court evidence submit <caseId> <description> <zoneId> "
                                    + "| list <caseId> [afterSeq] [limit] - evidence"
                    ),
                    new Entry(
                            "court.verdict",
                            "  /fr court verdict issue <caseId> <outcome> <zoneId> "
                                    + "<reasoning> | show <verdictId> - verdicts"
                    ),
                    new Entry(
                            "court.review",
                            "  /fr court review request <caseId> <zoneId> "
                                    + "| decide <caseId> <zoneId> - review"
                    )
            )
    );

    private HelpCommand() {
    }

    /**
     * Fresh help child for the /fr root. The module argument is a plain word so
     * unknown input is rejected at execution time with one bounded message.
     */
    static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("help")
                .executes(HelpCommand::showIndex)
                .then(Commands.argument("module", StringArgumentType.word())
                        .executes(HelpCommand::showModule));
    }

    /** All language keys used by the guide (for the language-file completeness test). */
    static List<String> languageKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(KEY_PREFIX + "index.title");
        for (String module : KNOWN_MODULES) {
            keys.add(KEY_PREFIX + "index.line." + module);
        }
        keys.add(KEY_PREFIX + "module.unknown");
        for (Page page : List.of(MONEY_PAGE, BANK_PAGE, CITIZEN_PAGE, GOVERNMENT_PAGE,
                PARLIAMENT_PAGE, COURT_PAGE)) {
            keys.add(KEY_PREFIX + page.titleKey());
            for (Entry entry : page.entries()) {
                keys.add(KEY_PREFIX + entry.keySuffix());
            }
        }
        keys.add(KEY_PREFIX + "institution.title");
        keys.add(KEY_PREFIX + "institution.restricted");
        return List.copyOf(keys);
    }

    // ------------------------------------------------------------------
    // /fr help
    // ------------------------------------------------------------------

    private static int showIndex(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        send(
                source,
                KEY_PREFIX + "index.title",
                "FontaineRepublic help. Modules: use /fr help <module> for details."
        );
        for (String module : KNOWN_MODULES) {
            send(source, KEY_PREFIX + "index.line." + module, indexLine(module));
        }
        return CommandFeedback.SUCCESS;
    }

    private static String indexLine(String module) {
        return switch (module) {
            case "money" -> "  money: personal money commands";
            case "bank" -> "  bank: central-bank official duties (on-site)";
            case "citizen" -> "  citizen: own citizen status";
            case "government" -> "  government: ministries, positions, appointments";
            case "parliament" -> "  parliament: proposals, votes, bills";
            case "court" -> "  court: cases, evidence, verdicts";
            case "institution" -> "  institution: institution administration (operator only)";
            default -> throw new IllegalArgumentException("Unknown module " + module);
        };
    }

    // ------------------------------------------------------------------
    // /fr help <module>
    // ------------------------------------------------------------------

    private static int showModule(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String module = StringArgumentType.getString(context, "module");
        if (!KNOWN_MODULES.contains(module)) {
            send(
                    source,
                    KEY_PREFIX + "module.unknown",
                    "Unknown module. Use /fr help for the module list."
            );
            return CommandFeedback.FAILURE;
        }
        if (module.equals("institution")) {
            // Restricted page: states the operator-only boundary without
            // enumerating the hidden administration commands.
            send(
                    source,
                    KEY_PREFIX + "institution.title",
                    "institution: institution administration (operator only)."
            );
            send(
                    source,
                    KEY_PREFIX + "institution.restricted",
                    "  Access is restricted to server operators; no command details "
                            + "are listed here."
            );
            return CommandFeedback.SUCCESS;
        }
        Page page = switch (module) {
            case "money" -> MONEY_PAGE;
            case "bank" -> BANK_PAGE;
            case "citizen" -> CITIZEN_PAGE;
            case "government" -> GOVERNMENT_PAGE;
            case "parliament" -> PARLIAMENT_PAGE;
            case "court" -> COURT_PAGE;
            default -> throw new IllegalStateException("Unreachable module " + module);
        };
        send(source, KEY_PREFIX + page.titleKey(), page.titleFallback());
        for (Entry entry : page.entries()) {
            send(source, KEY_PREFIX + entry.keySuffix(), entry.fallback());
        }
        return CommandFeedback.SUCCESS;
    }

    private static void send(CommandSourceStack source, String key, String fallback) {
        source.sendSuccess(
                () -> Component.translatableWithFallback(key, fallback),
                false
        );
    }

    /** One bounded module page: a title plus fixed command entries. */
    private record Page(String titleKey, String titleFallback, List<Entry> entries) {
        private Page {
            titleKey = Objects.requireNonNull(titleKey, "titleKey");
            titleFallback = Objects.requireNonNull(titleFallback, "titleFallback");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }

    /** One bounded command entry: language key suffix plus English fallback. */
    private record Entry(String keySuffix, String fallback) {
        private Entry {
            keySuffix = Objects.requireNonNull(keySuffix, "keySuffix");
            fallback = Objects.requireNonNull(fallback, "fallback");
        }
    }
}
