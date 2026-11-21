package dev.rosewood.rosechat.command.friend;

import dev.rosewood.rosechat.chat.PlayerData;
import dev.rosewood.rosechat.command.RoseChatCommand;
import dev.rosewood.rosechat.command.argument.RoseChatArgumentHandlers;
import dev.rosewood.rosechat.message.MessageUtils;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.framework.ArgumentsDefinition;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;

public class FriendAddCommand extends RoseChatCommand {

    public FriendAddCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("add")
                .aliases("invite")
                .descriptionKey("command-friend-add-description")
                .permission("rosechat.friend")
                .playerOnly(true)
                .arguments(ArgumentsDefinition.builder()
                        .required("player", RoseChatArgumentHandlers.ROSE_PLAYER)
                        .build())
                .build();
    }

    @RoseExecutable
    public void execute(CommandContext context, RosePlayer target) {
        RosePlayer player = new RosePlayer(context.getSender());
        if (!target.isPlayer()) {
            player.sendLocaleMessage("invalid-argument",
                    StringPlaceholders.of("message", this.getLocaleManager().getLocaleMessage("argument-handler-player")));
            return;
        }

        if (target.getUUID().equals(player.getUUID())) {
            player.sendLocaleMessage("command-friend-cannot-self");
            return;
        }

        if (this.getAPI().isBlocked(player.getUUID(), target.getUUID())) {
            player.sendLocaleMessage("command-block-cannot-message");
            return;
        }

        PlayerData playerData = player.getPlayerData();
        PlayerData targetData = target.getPlayerData();
        if (playerData == null || targetData == null)
            return;

        if (playerData.isFriend(target.getUUID())) {
            player.sendLocaleMessage("command-friend-already-friends",
                    StringPlaceholders.of("player", target.getName()));
            return;
        }

        if (targetData.getIgnoringPlayers().contains(player.getUUID())) {
            player.sendLocaleMessage("command-togglemessage-cannot-message");
            return;
        }

        // If target already sent us a request, accept it immediately.
        if (playerData.getFriendRequests().contains(target.getUUID())) {
            FriendAcceptCommand.acceptRequest(player, playerData, target.getUUID());
            return;
        }

        if (targetData.getFriendRequests().contains(player.getUUID())) {
            player.sendLocaleMessage("command-friend-already-requested",
                    StringPlaceholders.of("player", target.getName()));
            return;
        }

        targetData.addFriendRequest(player.getUUID());

        player.sendLocaleMessage("command-friend-add-sent",
                StringPlaceholders.of("player", target.getName()));
        target.sendLocaleMessage("command-friend-add-received",
                StringPlaceholders.of("player", player.getName()));

        ComponentBuilder builder = new ComponentBuilder();
        builder.append("          ");
        builder.append(MessageUtils.clickableButton(
                target,
                this.getLocaleManager().getMessage("command-friend-accept-button"),
                this.getLocaleManager().getMessage("command-friend-accept-hover"),
                ClickEvent.Action.RUN_COMMAND,
                "/friend accept " + player.getRealName()));
        builder.append("          ", ComponentBuilder.FormatRetention.NONE);
        builder.append(MessageUtils.clickableButton(
                target,
                this.getLocaleManager().getMessage("command-friend-deny-button"),
                this.getLocaleManager().getMessage("command-friend-deny-hover"),
                ClickEvent.Action.RUN_COMMAND,
                "/friend deny " + player.getRealName()), ComponentBuilder.FormatRetention.NONE);

        target.send(builder.create());
    }

}
