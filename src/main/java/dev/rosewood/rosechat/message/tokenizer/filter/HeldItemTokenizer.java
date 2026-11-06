package dev.rosewood.rosechat.message.tokenizer.filter;

import dev.rosewood.rosechat.api.RoseChatAPI;
import dev.rosewood.rosechat.chat.filter.Filter;
import dev.rosewood.rosechat.config.Settings;
import dev.rosewood.rosechat.manager.FilterManager.FilterPattern;
import dev.rosewood.rosechat.message.MessageUtils;
import dev.rosewood.rosechat.message.tokenizer.Token;
import dev.rosewood.rosechat.message.tokenizer.Token.PlayerInputState;
import dev.rosewood.rosechat.message.tokenizer.Tokenizer;
import dev.rosewood.rosechat.message.tokenizer.TokenizerParams;
import dev.rosewood.rosechat.message.tokenizer.TokenizerResult;
import dev.rosewood.rosechat.message.tokenizer.Tokenizers;
import dev.rosewood.rosechat.message.tokenizer.decorator.HoverDecorator;
import dev.rosewood.rosegarden.utils.NMSUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.commons.text.WordUtils;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class HeldItemTokenizer extends Tokenizer {

    public static Tokenizer HELD_ITEM_TOKENIZER;

    private final RoseChatAPI api;

    public HeldItemTokenizer() {
        super("held_item");

        this.api = RoseChatAPI.getInstance();

        // Example of how to register tokenizers
        if (HELD_ITEM_TOKENIZER == null) {
            HELD_ITEM_TOKENIZER = this;
            Tokenizers.DEFAULT_BUNDLE.registerBefore(Tokenizers.ROSECHAT_PLACEHOLDER, this);
            Tokenizers.DEFAULT_DISCORD_BUNDLE.registerBefore(Tokenizers.ROSECHAT_PLACEHOLDER, this);
        } else {
            throw new IllegalStateException("Cannot instantiate more than one HeldItemTokenizer");
        }
    }

    @Override
    public List<TokenizerResult> tokenize(TokenizerParams params) {
        Filter filter = this.api.getFilterById(Settings.HELD_ITEM_FILTER.get());
        if (filter == null)
            return null;

        if (!params.getSender().isPlayer())
            return null;

        ItemStack itemStack = params.getSender().asPlayer().getInventory().getItemInMainHand();
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null)
            return null;

        List<TokenizerResult> results = new ArrayList<>();

        for (Pattern pattern : this.api.getFilterManager().getCompiledPatterns(filter.id(), FilterPattern.MATCHES))
            this.collectMatches(itemStack, itemMeta, pattern, filter.replacement(), params, results);

        return results;
    }

    private void collectMatches(ItemStack itemStack, ItemMeta itemMeta, Pattern pattern, String replacement, TokenizerParams params, List<TokenizerResult> results) {
        String input = params.getInput();
        Matcher matcher = pattern.matcher(input);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String match = matcher.group();
            if (TokenizerResult.overlaps(results, start, end))
                continue;

            if (start > 0 && input.charAt(start - 1) == MessageUtils.ESCAPE_CHAR && checkPermission(params, "rosechat.escape")) {
                results.add(new TokenizerResult(Token.text(match), start - 1, match.length() + 1));
                continue;
            }

            if (!this.hasTokenPermission(params, "rosechat.helditem"))
                continue;

            int amount = itemStack.getAmount();
            String json = !NMSUtil.isPaper() || replacement.contains("%item%") ? itemMeta.getAsString() : "";

            String itemName;
            if (NMSUtil.isPaper()) {
                itemName = PaperItemHelper.getItemName(itemStack, itemMeta);
            } else if (itemMeta.hasDisplayName()) {
                itemName = itemMeta.getDisplayName();
            } else if (NMSUtil.getVersionNumber() >= 21 && itemMeta.hasItemName()) {
                itemName = itemMeta.getItemName();
            } else {
                itemName = WordUtils.capitalize(itemStack.getType().name().toLowerCase().replace("_", " "));
            }

            results.add(new TokenizerResult(Token.group(replacement)
                    .decorate(new HoverDecorator(itemStack, json))
                    .placeholder("item_name", itemName)
                    .placeholder("item", json)
                    .placeholder("amount", amount)
                    .ignoreTokenizer(this)
                    .ignoreTokenizer(Tokenizers.FILTER)
                    .playerInputState(PlayerInputState.NOT_PLAYER_INPUT)
                    .encapsulate()
                    .build(), start, match.length()));
        }
    }

    private static final class PaperItemHelper {

        private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
                .character('&')
                .hexColors()
                .build();

        private static String getItemName(ItemStack itemStack, ItemMeta itemMeta) {
            if (itemMeta.hasDisplayName()) {
                Component displayName = itemMeta.displayName();
                if (displayName != null)
                    return SERIALIZER.serialize(displayName);
            }
            if (NMSUtil.getVersionNumber() >= 21 && itemMeta.hasItemName()) {
                return itemMeta.getItemName();
            }
            return WordUtils.capitalize(itemStack.getType().name().toLowerCase().replace("_", " "));
        }

    }

}
