package dev.rosewood.rosechat.command.friend;

import dev.rosewood.rosechat.api.RoseChatAPI;
import dev.rosewood.rosechat.chat.PlayerData;
import dev.rosewood.rosechat.command.RoseChatCommand;
import dev.rosewood.rosechat.message.MessageUtils;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.framework.Argument;
import dev.rosewood.rosegarden.command.framework.ArgumentHandler;
import dev.rosewood.rosegarden.command.framework.ArgumentsDefinition;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.InputIterator;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class FriendDenyCommand extends RoseChatCommand {

    public FriendDenyCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("deny")
                .aliases("decline")
                .descriptionKey("command-friend-deny-description")
                .permission("rosechat.friend")
                .playerOnly(true)
                .arguments(ArgumentsDefinition.builder()
                        .optional("player", new ArgumentHandler<String>(String.class) {
                            @Override
                            public String handle(CommandContext context, Argument argument, InputIterator inputIterator) {
                                return inputIterator.next();
                            }

                            @Override
                            public List<String> suggest(CommandContext context, Argument argument, String[] args) {
                                if (!(context.getSender() instanceof Player sender))
                                    return Collections.emptyList();
                                PlayerData data = RoseChatAPI.getInstance().getPlayerData(sender.getUniqueId());
                                if (data == null)
                                    return Collections.emptyList();
                                return data.getFriendRequests().stream()
                                        .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                                        .filter(Objects::nonNull)
                                        .toList();
                            }
                        })
                        .build())
                .build();
    }

    @RoseExecutable
    public void execute(CommandContext context) {
        RosePlayer player = new RosePlayer(context.getSender());
        PlayerData data = player.getPlayerData();
        if (data == null || data.getFriendRequests().isEmpty()) {
            player.sendLocaleMessage("command-friend-no-requests");
            return;
        }

        UUID latest = data.getFriendRequests().get(data.getFriendRequests().size() - 1);
        this.deny(player, data, latest);
    }

    @RoseExecutable
    public void execute(CommandContext context, String targetName) {
        RosePlayer player = new RosePlayer(context.getSender());
        PlayerData data = player.getPlayerData();
        if (data == null || data.getFriendRequests().isEmpty()) {
            player.sendLocaleMessage("command-friend-no-requests");
            return;
        }

        UUID matched = null;
        for (UUID uuid : data.getFriendRequests()) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(targetName)) {
                matched = uuid;
                break;
            }
        }

        if (matched == null) {
            player.sendLocaleMessage("command-friend-request-not-found",
                    StringPlaceholders.of("player", targetName));
            return;
        }

        this.deny(player, data, matched);
    }

    private void deny(RosePlayer player, PlayerData data, UUID requesterUuid) {
        data.removeFriendRequest(requesterUuid);
        player.sendLocaleMessage("command-friend-deny-success",
                StringPlaceholders.of("player", MessageUtils.resolvePlayerName(requesterUuid)));

        Player onlineRequester = Bukkit.getPlayer(requesterUuid);
        if (onlineRequester != null) {
            new RosePlayer(onlineRequester).sendLocaleMessage("command-friend-deny-denied",
                    StringPlaceholders.of("player", player.getName()));
        }
    }

}
