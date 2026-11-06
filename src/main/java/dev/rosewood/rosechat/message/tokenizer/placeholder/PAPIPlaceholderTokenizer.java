package dev.rosewood.rosechat.message.tokenizer.placeholder;

import dev.rosewood.rosechat.message.MessageUtils;
import dev.rosewood.rosechat.message.tokenizer.Token;
import dev.rosewood.rosechat.message.tokenizer.Token.PlayerInputState;
import dev.rosewood.rosechat.message.tokenizer.Tokenizer;
import dev.rosewood.rosechat.message.tokenizer.TokenizerParams;
import dev.rosewood.rosechat.message.tokenizer.TokenizerResult;
import dev.rosewood.rosegarden.hook.PlaceholderAPIHook;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class PAPIPlaceholderTokenizer extends Tokenizer {

    private static final Pattern PATTERN = Pattern.compile("%(.*?)%");
    private final boolean isBungee;

    public PAPIPlaceholderTokenizer(boolean isBungee) {
        super(isBungee ? "bungee_papi_placeholder" : "papi_placeholder");
        this.isBungee = isBungee;
    }

    @Override
    public List<TokenizerResult> tokenize(TokenizerParams params) {
        String input = params.getInput();

        List<TokenizerResult> results = new ArrayList<>();
        Matcher matcher = PATTERN.matcher(input);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            String placeholder = matcher.group();
            String placeholderPermission = placeholder.replaceFirst("_", ".");
            if (!this.hasExtendedTokenPermission(params, "rosechat.placeholders", "rosechat.placeholder." + placeholderPermission))
                continue;

            if (start > 0 && input.charAt(start - 1) == MessageUtils.ESCAPE_CHAR && checkPermission(params, "rosechat.escape")) {
                results.add(new TokenizerResult(Token.text(placeholder), start - 1, placeholder.length() + 1));
                continue;
            }

            String content;
            if (placeholder.startsWith("%other_") && !this.isBungee) {
                OfflinePlayer offlineReceiver = params.getReceiver().asOfflinePlayer();
                String inner = placeholder.replaceFirst("other_", "");
                content = inner.startsWith("%rel_")
                        ? PlaceholderAPIHook.applyRelationalPlaceholders(params.getSender().asPlayer(), params.getReceiver().asPlayer(), inner)
                        : inner;
                content = PlaceholderAPIHook.applyPlaceholders(offlineReceiver, content);
            } else {
                OfflinePlayer offlinePlayer = params.getSender().asOfflinePlayer();
                content = placeholder.startsWith("%rel_")
                        ? PlaceholderAPIHook.applyRelationalPlaceholders(params.getSender().asPlayer(), params.getReceiver().asPlayer(), placeholder)
                        : placeholder;
                content = PlaceholderAPIHook.applyPlaceholders(offlinePlayer, content);
            }

            // If we haven't changed, don't allow tokenizing this text anymore
            if (Objects.equals(content, placeholder)) {
                results.add(new TokenizerResult(Token.text(placeholder), start, placeholder.length()));
                continue;
            }

            Token.Builder token = Token.group(content).playerInputState(PlayerInputState.NOT_PLAYER_INPUT);

            // Don't encapsulate if the placeholder only contains a colour or ends with a colour
            // Ignore everything that definitely isn't a colour.
            if (content.contains(ChatColor.COLOR_CHAR + "") || content.contains("&") || content.contains("#") || content.contains("<") || content.contains("{")) {
                if (this.checkEncapsulation(content,
                        MessageUtils.LEGACY_REGEX_COMBINED,
                        MessageUtils.SPIGOT_HEX_REGEX_COMBINED,
                        MessageUtils.GRADIENT_PATTERN,
                        MessageUtils.RAINBOW_PATTERN))
                    token.encapsulate();
            }

            results.add(new TokenizerResult(token.build(), start, placeholder.length()));
        }

        return results;
    }

    private boolean checkEncapsulation(String content, Pattern... patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(content);
            while (matcher.find())
                if (content.trim().equals(matcher.group()) || content.trim().endsWith(matcher.group()))
                    return false;
        }
        return true;
    }

}
