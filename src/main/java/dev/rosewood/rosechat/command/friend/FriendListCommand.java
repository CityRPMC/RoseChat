package dev.rosewood.rosechat.command.friend;

import dev.rosewood.rosechat.api.RoseChatAPI;
import dev.rosewood.rosechat.chat.PlayerData;
import dev.rosewood.rosechat.command.RoseChatCommand;
import dev.rosewood.rosechat.manager.LocaleManager;
import dev.rosewood.rosechat.message.MessageUtils;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class FriendListCommand extends RoseChatCommand {

    public FriendListCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("list")
                .descriptionKey("command-friend-list-description")
                .permission("rosechat.friend")
                .playerOnly(true)
                .build();
    }

    @RoseExecutable
    public void execute(CommandContext context) {
        displayFriendList(new RosePlayer(context.getSender()));
    }

    public static void displayFriendList(RosePlayer player) {
        PlayerData data = player.getPlayerData();
        if (data == null)
            return;

        LocaleManager localeManager = RoseChatAPI.getInstance().getLocaleManager();
        List<UUID> friends = new ArrayList<>(data.getFriends());
        player.sendLocaleMessage("command-friend-list-title",
                StringPlaceholders.of("amount", friends.size()));

        if (friends.isEmpty())
            return;

        friends.sort(Comparator.comparing((UUID uuid) -> {
            Player online = Bukkit.getPlayer(uuid);
            return online != null && !MessageUtils.isPlayerVanished(online) ? 0 : 1;
        }).thenComparing(MessageUtils::resolvePlayerName, String.CASE_INSENSITIVE_ORDER));

        String msgLabel = localeManager.getMessage("command-friend-list-msg-button");
        String profileLabel = localeManager.getMessage("command-friend-list-profile-button");

        for (UUID uuid : friends) {
            Player online = Bukkit.getPlayer(uuid);
            boolean isOnline = online != null && !MessageUtils.isPlayerVanished(online);
            String displayName = MessageUtils.resolvePlayerName(uuid);
            String rawName = online != null ? online.getName() : Bukkit.getOfflinePlayer(uuid).getName();
            if (rawName == null)
                rawName = displayName;

            String prefix = isOnline
                    ? "<#CCFFCC>● <#FFAA00>" + displayName + " <#8E99A2>(Online) "
                    : "<#8E99A2>○ " + displayName + " (Offline) ";

            ComponentBuilder builder = new ComponentBuilder();
            builder.append(MessageUtils.clickableButton(player, prefix, null, null, null));

            if (isOnline) {
                builder.append(MessageUtils.clickableButton(
                        player,
                        msgLabel,
                        "<#CCCCFF>Click to message " + displayName,
                        ClickEvent.Action.SUGGEST_COMMAND,
                        "/msg " + rawName + " "), ComponentBuilder.FormatRetention.NONE);
                builder.append(" ", ComponentBuilder.FormatRetention.NONE);
            }

            builder.append(MessageUtils.clickableButton(
                    player,
                    profileLabel,
                    "<#CCCCFF>Click to view profile",
                    ClickEvent.Action.RUN_COMMAND,
                    "/profile " + rawName), ComponentBuilder.FormatRetention.NONE);

            player.send(builder.create());
        }
    }

}
