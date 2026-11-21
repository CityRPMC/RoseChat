package dev.rosewood.rosechat.command.friend;

import dev.rosewood.rosechat.RoseChat;
import dev.rosewood.rosechat.api.RoseChatAPI;
import dev.rosewood.rosechat.chat.PlayerData;
import dev.rosewood.rosechat.command.RoseChatCommand;
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
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class FriendRemoveCommand extends RoseChatCommand {

    public FriendRemoveCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("remove")
                .aliases("delete", "unfriend")
                .descriptionKey("command-friend-remove-description")
                .permission("rosechat.friend")
                .playerOnly(true)
                .arguments(ArgumentsDefinition.builder()
                        .required("player", new ArgumentHandler<String>(String.class) {
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
                                return data.getFriends().stream()
                                        .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                                        .filter(Objects::nonNull)
                                        .toList();
                            }
                        })
                        .build())
                .build();
    }

    @RoseExecutable
    public void execute(CommandContext context, String targetName) {
        Bukkit.getScheduler().runTaskAsynchronously(RoseChat.getInstance(), () -> {
            RosePlayer player = new RosePlayer(context.getSender());
            PlayerData playerData = player.getPlayerData();
            if (playerData == null)
                return;

            RosePlayer target = this.findPlayer(targetName);
            if (target == null) {
                for (var friendUuid : playerData.getFriends()) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(friendUuid);
                    if (offline.getName() != null && offline.getName().equalsIgnoreCase(targetName)) {
                        target = new RosePlayer(offline);
                        break;
                    }
                }
            }

            if (target == null || !playerData.isFriend(target.getUUID())) {
                player.sendLocaleMessage("command-friend-not-friends",
                        StringPlaceholders.of("player", target != null ? target.getName() : targetName));
                return;
            }

            final RosePlayer finalTarget = target;
            playerData.removeFriend(finalTarget.getUUID());
            player.sendLocaleMessage("command-friend-remove-success",
                    StringPlaceholders.of("player", finalTarget.getName()));

            this.getAPI().getPlayerData(finalTarget.getUUID(), targetData -> {
                if (targetData != null) {
                    targetData.getFriends().remove(player.getUUID());
                    targetData.getFriendRequests().remove(player.getUUID());
                }
                if (finalTarget.isPlayer()) {
                    finalTarget.sendLocaleMessage("command-friend-remove-removed",
                            StringPlaceholders.of("player", player.getName()));
                }
            });
        });
    }

}
